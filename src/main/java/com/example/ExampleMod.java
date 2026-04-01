package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExampleMod implements ModInitializer {
	public static final String MOD_ID = "modid";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> { //処理をイベントに登録
			//既にチェック済みの個体（再読み込みや侵入者）はスルーする
			if (entity.getTags().contains("sg_checked")) return;

			if (world.dimension().equals(Level.NETHER)) { //ディメンション == ネザー
				if (entity.getType().equals(EntityType.GHAST)) { //エンティティ == ガスト
					double x = entity.getX();
					double y = entity.getY();
					double z = entity.getZ();

					if (x >= 0 && x <= 20 && y >= 50 && y <= 100 && z >= 0 && z <= 20) { //エンティティのスポーンを破棄する空間
						LOGGER.info("Ghast discarded at: {}, {}, {}", x, y, z);
						entity.discard(); //エンティティを破棄
						return;
					}
				}
			}
			//範囲外で生き残った新規個体には「チェック済み」タグを付与し、次回以降のロードで消えないようにする
			entity.addTag("sg_checked");
		});
	}
}