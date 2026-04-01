package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class ExampleMod implements ModInitializer {
	public static final String MOD_ID = "modid";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

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

			if (world.dimension().equals(Level.NETHER)) { //ディメンション == ネザー
				if (entity.getType().equals(EntityType.GHAST)) { //エンティティ == ガスト
					//足元を探索するための座標オブジェクトを作成
					BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(entity.getX(), entity.getY(), entity.getZ());

					//ガストの現在位置から、世界の最下層に向かって1マスずつ下がって確認する
					for (int y = (int) entity.getY(); y >= world.getMinY(); y--) {
						pos.setY(y);
						BlockState state = world.getBlockState(pos);

						//空気ブロック以外（＝何かしらの固形ブロック）にぶつかったら判定開始
						if (!state.isAir()) {
							Block groundBlock = state.getBlock();

							//そのブロックが「自然生成ブロック」のリストに含まれていない場合（＝人工物）
							if (!NATURAL_NETHER_BLOCKS.contains(groundBlock)) {
								LOGGER.info("Ghast discarded above artificial block: {}", groundBlock.getName().getString());
								entity.discard();
								return; //破棄したらここで処理終了
							}
							//自然生成ブロックだった場合は、安全な湧きなので探索ループを抜ける
							break;
						}
					}
				}
			}
			//範囲外で生き残った新規個体には「チェック済み」タグを付与し、次回以降のロードで消えないようにする
			entity.addTag("sg_checked");
		});
	}
}