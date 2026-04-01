package com.lukemyb.spawnblocker;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class SpawnBlocker implements ModInitializer {
	public static final String MOD_ID = "spawn_blocker";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// 環境（バイオーム・構造物）の種類を定義する列挙型
	public enum EnvironmentType {
		NETHER_FORTRESS, BASTION_REMNANT,
		NETHER_WASTES, CRIMSON_FOREST, WARPED_FOREST, SOUL_SAND_VALLEY, BASALT_DELTAS,
		OTHER
	}

	//スキャンを貫通して下の地面をチェックさせる非固形ブロックのリスト
	private static final Set<Block> PASS_THROUGH_BLOCKS = Set.of(
			Blocks.FIRE, Blocks.SOUL_FIRE,
			Blocks.WARPED_ROOTS, Blocks.CRIMSON_ROOTS, Blocks.NETHER_SPROUTS,
			Blocks.WARPED_FUNGUS, Blocks.CRIMSON_FUNGUS,
			Blocks.TWISTING_VINES, Blocks.TWISTING_VINES_PLANT,
			Blocks.WEEPING_VINES, Blocks.WEEPING_VINES_PLANT
	);

	//環境ごとの「自然生成ブロック」リストを定義
	private static final Set<Block> GENERAL_NETHER_BLOCKS = Set.of(
			Blocks.NETHERRACK, Blocks.SOUL_SAND, Blocks.SOUL_SOIL, Blocks.BASALT, Blocks.BLACKSTONE,
			Blocks.MAGMA_BLOCK, Blocks.LAVA, Blocks.CRIMSON_NYLIUM, Blocks.WARPED_NYLIUM,
			Blocks.BONE_BLOCK, Blocks.GLOWSTONE, Blocks.NETHER_QUARTZ_ORE, Blocks.NETHER_GOLD_ORE, Blocks.ANCIENT_DEBRIS,
			Blocks.WARPED_WART_BLOCK, Blocks.NETHER_WART_BLOCK, Blocks.SHROOMLIGHT
			// 砂利(Gravel)は建築材として使うため除外
	);

	private static final Set<Block> FORTRESS_BLOCKS = Set.of(
			Blocks.NETHER_BRICKS //ネザー要塞はネザーレンガのみ許可
	);

	private static final Set<Block> BASTION_BLOCKS = Set.of(
			Blocks.NETHERRACK, Blocks.SOUL_SAND, Blocks.SOUL_SOIL, Blocks.BASALT, Blocks.BLACKSTONE,
			Blocks.MAGMA_BLOCK, Blocks.LAVA, Blocks.CRIMSON_NYLIUM, Blocks.WARPED_NYLIUM,
			Blocks.BONE_BLOCK, Blocks.GLOWSTONE, Blocks.NETHER_QUARTZ_ORE, Blocks.NETHER_GOLD_ORE, Blocks.ANCIENT_DEBRIS,
			Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS,
			Blocks.GILDED_BLACKSTONE, Blocks.CHISELED_POLISHED_BLACKSTONE, Blocks.POLISHED_BASALT, Blocks.SMOOTH_BASALT,
			Blocks.WARPED_WART_BLOCK, Blocks.NETHER_WART_BLOCK, Blocks.SHROOMLIGHT
	);

	// 指定した座標の環境を取得するヘルパーメソッド
	private EnvironmentType getEnvironmentAt(ServerLevel serverLevel, BlockPos pos) {
		//構造物の判定 (ネザー要塞と廃要塞)
		var structureRegistry = serverLevel.registryAccess().lookupOrThrow(Registries.STRUCTURE);

		var fortress = structureRegistry.getOrThrow(BuiltinStructures.FORTRESS).value();
		if (serverLevel.structureManager().getStructureWithPieceAt(pos, fortress).isValid()) {
			return EnvironmentType.NETHER_FORTRESS;
		}

		var bastion = structureRegistry.getOrThrow(BuiltinStructures.BASTION_REMNANT).value();
		if (serverLevel.structureManager().getStructureWithPieceAt(pos, bastion).isValid()) {
			return EnvironmentType.BASTION_REMNANT;
		}

		//バイオームの判定
		var biome = serverLevel.getBiome(pos);
		if (biome.is(Biomes.NETHER_WASTES)) return EnvironmentType.NETHER_WASTES;
		if (biome.is(Biomes.CRIMSON_FOREST)) return EnvironmentType.CRIMSON_FOREST;
		if (biome.is(Biomes.WARPED_FOREST)) return EnvironmentType.WARPED_FOREST;
		if (biome.is(Biomes.SOUL_SAND_VALLEY)) return EnvironmentType.SOUL_SAND_VALLEY;
		if (biome.is(Biomes.BASALT_DELTAS)) return EnvironmentType.BASALT_DELTAS;

		return EnvironmentType.OTHER;
	}

	//湧きを許可する「ネザーの自然生成ブロック」のリスト
	private static final Set<Block> NATURAL_NETHER_BLOCKS = Set.of(
			Blocks.LAVA,
			Blocks.NETHERRACK,
			Blocks.SOUL_SAND,
			Blocks.SOUL_SOIL,
			Blocks.BASALT,
			Blocks.BLACKSTONE,
			Blocks.MAGMA_BLOCK,
			Blocks.CRIMSON_NYLIUM,
			Blocks.WARPED_NYLIUM,
			//Blocks.GRAVEL,
			Blocks.BONE_BLOCK,
			Blocks.GLOWSTONE,
			Blocks.NETHER_QUARTZ_ORE,
			Blocks.NETHER_GOLD_ORE,
			Blocks.ANCIENT_DEBRIS
	);

	@Override
	public void onInitialize() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> { //処理をイベントに登録
			//既にチェック済みの個体（再読み込みや侵入者）はスルーする
			if (entity.getTags().contains("sg_checked")) return;

			//モブ（生き物）以外は処理しない（アイテム消滅バグの防止）
			if (!(entity instanceof Mob)) return;

			if (world.dimension().equals(Level.NETHER) && world instanceof ServerLevel serverLevel) {
				BlockPos pos = entity.blockPosition();
				EnvironmentType env = getEnvironmentAt(serverLevel, pos);
				LOGGER.info("Spawn detected. Entity: {}, Environment: {}", entity.getType().getDescription().getString(), env);

				// 現在の環境に応じた許可ブロックリストを取得
				Set<Block> allowedBlocks = switch (env) {
					case NETHER_FORTRESS -> FORTRESS_BLOCKS;
					case BASTION_REMNANT -> BASTION_BLOCKS;
					default -> GENERAL_NETHER_BLOCKS;
				};

				//足元を探索するための座標オブジェクトを作成
				BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos(entity.getX(), entity.getY(), entity.getZ());

				//現在位置から、世界の最下層に向かって1マスずつ下がって確認する
				for (int y = (int) entity.getY(); y >= world.getMinY(); y--) {
					mutablePos.setY(y);
					BlockState state = world.getBlockState(mutablePos);

					// 空気、または PASS_THROUGH_BLOCKS に含まれる植物・炎の場合はスキャンを続行する
					if (!state.isAir() && !PASS_THROUGH_BLOCKS.contains(state.getBlock())) {
						Block groundBlock = state.getBlock();

						// 足元のブロックが、その環境の許可リストに無ければ破棄
						if (!allowedBlocks.contains(groundBlock)) {
							LOGGER.info("{} discarded above artificial block: {} in {}",
								entity.getType().getDescription().getString(),
								groundBlock.getName().getString(),
								env);
							entity.discard();
							return;
						}
						break;
					}
				}
			}
			//範囲外で生き残った新規個体には「チェック済み」タグを付与し、次回以降のロードで消えないようにする
			entity.addTag("sg_checked");
		});
	}
}