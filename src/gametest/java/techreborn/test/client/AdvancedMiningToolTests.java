/*
 * This file is part of TechReborn, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2026 TechReborn
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package techreborn.test.client;

import static techreborn.test.client.ClientTestHarness.charged;
import static techreborn.test.client.ClientTestHarness.energy;
import static techreborn.test.client.ClientTestHarness.held;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;

import java.util.stream.Stream;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import reborncore.common.blockentity.MachineBaseBlockEntity;
import reborncore.common.blocks.BlockMachineBase;
import techreborn.blocks.misc.BlockRubberLog;
import techreborn.component.TRDataComponentTypes;
import techreborn.init.TRContent;
import techreborn.utils.TRItemUtils;

final class AdvancedMiningToolTests {
	private static final BlockPos TARGET = new BlockPos(3, ClientTestHarness.TEST_Y + 2, 0);

	private AdvancedMiningToolTests() {
	}

	static void run(ClientTestHarness test) {
		testIndustrialDrillThreeByThreeAndSafetyFilters(test);
		testIndustrialJackhammerModeCycleAndFiveByFive(test);
		testIndustrialChainsawFellsConnectedTree(test);
		testIndustrialChainsawHonorsTraversalLimit(test);
		testOmniToolMiningAndWrenchEnergy(test);
		testRockCutterSilkTouch(test);
		testRockCutterDropsAndEnchanting(test);
		testElectricTreetap(test);
	}

	private static void testIndustrialDrillThreeByThreeAndSafetyFilters(ClientTestHarness test) {
		reset(test);
		fillWall(test, 1, Blocks.STONE);
		BlockPos protectedPos = TARGET.offset(0, 1, 1);
		test.setBlock(protectedPos, Blocks.BEDROCK);
		ItemStack drill = charged(TRContent.INDUSTRIAL_DRILL);
		test.setHotbarItem(drill);
		test.runCommand("gamemode survival @a");
		test.sneakUseHeldItem();
		test.assertServer(server -> TRItemUtils.isActive(held(server)),
			"Real sneak-use did not enable the industrial drill AOE mode");
		test.screenshot(TARGET, "tools-industrial-drill-three-by-three-ready");
		test.mineBlockWithInput(TARGET, 30);
		test.waitForServer(server -> countBlocks(server, Blocks.STONE, 1) == 0, 20,
			"Industrial drill did not clear its three-by-three stone footprint");
		test.assertServer(server -> ClientTestHarness.level(server).getBlockState(protectedPos).is(Blocks.BEDROCK),
			"Industrial drill AOE removed an unbreakable block");
		test.assertServer(server -> energy(held(server)) < energy(drill),
			"Industrial drill AOE did not consume energy");
		test.screenshot(TARGET, "tools-industrial-drill-three-by-three-result");
	}

	private static void testIndustrialJackhammerModeCycleAndFiveByFive(ClientTestHarness test) {
		reset(test);
		fillWall(test, 2, Blocks.STONE);
		BlockPos wrongMaterial = TARGET.offset(0, 2, 2);
		test.setBlock(wrongMaterial, Blocks.OAK_LOG);
		test.setHotbarItem(charged(TRContent.INDUSTRIAL_JACKHAMMER));
		test.runCommand("gamemode survival @a");
		test.sneakUseHeldItem();
		test.assertServer(server -> TRItemUtils.isActive(held(server))
			&& !Boolean.TRUE.equals(held(server).get(TRDataComponentTypes.AOE5)),
			"First jackhammer mode input did not select three-by-three");
		test.sneakUseHeldItem();
		test.assertServer(server -> TRItemUtils.isActive(held(server))
			&& Boolean.TRUE.equals(held(server).get(TRDataComponentTypes.AOE5)),
			"Second jackhammer mode input did not select five-by-five");
		test.screenshotInventory("tools-industrial-jackhammer-five-by-five-mode");
		test.mineBlockWithInput(TARGET, 25);
		test.waitForServer(server -> countBlocks(server, Blocks.STONE, 2) == 0, 20,
			"Industrial jackhammer did not clear its five-by-five stone footprint");
		test.assertServer(server -> ClientTestHarness.level(server).getBlockState(wrongMaterial).is(Blocks.OAK_LOG),
			"Industrial jackhammer removed a block outside its mineable tag");
		test.screenshot(TARGET, "tools-industrial-jackhammer-five-by-five-result");
		test.sneakUseHeldItem();
		test.assertServer(server -> !TRItemUtils.isActive(held(server)),
			"Third jackhammer mode input did not disable AOE mining");
	}

	private static void testIndustrialChainsawFellsConnectedTree(ClientTestHarness test) {
		reset(test);
		BlockPos base = new BlockPos(2, ClientTestHarness.TEST_Y, 0);
		for (int y = 0; y < 4; y++) {
			test.setBlock(base.above(y), Blocks.OAK_LOG);
		}
		test.setBlock(base.above(2).east(), Blocks.OAK_LOG);
		test.setBlock(base.above(3).west(), Blocks.OAK_LOG);
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			test.setBlock(base.above(3).relative(direction), Blocks.OAK_LEAVES);
		}
		test.setHotbarItem(charged(TRContent.INDUSTRIAL_CHAINSAW));
		test.runCommand("gamemode survival @a");
		test.sneakUseHeldItem();
		test.screenshot(base.above(2), "tools-industrial-chainsaw-connected-tree-ready");
		test.mineBlockWithInput(base, 30);
		test.waitForServer(server -> {
			var level = ClientTestHarness.level(server);
			return level.getBlockState(base).isAir() && level.getBlockState(base.above(3)).isAir()
				&& level.getBlockState(base.above()).isAir()
				&& level.getBlockState(base.above(2)).isAir()
				&& level.getBlockState(base.above(2).east()).isAir()
				&& level.getBlockState(base.above(3).west()).isAir();
		}, 20, "Industrial chainsaw did not fell the connected tree");
		test.assertServer(server -> energy(held(server)) < energy(charged(TRContent.INDUSTRIAL_CHAINSAW)),
			"Industrial chainsaw did not consume energy for connected logs");
		test.screenshot(base.above(2), "tools-industrial-chainsaw-connected-tree-felled");
	}

	private static void testIndustrialChainsawHonorsTraversalLimit(ClientTestHarness test) {
		reset(test);
		BlockPos first = new BlockPos(2, ClientTestHarness.TEST_Y, 0);
		for (int x = 2; x <= 8; x++) {
			for (int z = -5; z <= 4; z++) {
				test.setBlock(new BlockPos(x, ClientTestHarness.TEST_Y, z), Blocks.OAK_LOG);
			}
		}
		test.setHotbarItem(charged(TRContent.INDUSTRIAL_CHAINSAW));
		test.runCommand("gamemode survival @a");
		test.sneakUseHeldItem();
		test.mineBlockWithInput(first, 30);
		test.assertServer(server -> {
			int remaining = 0;
			for (int x = 2; x <= 8; x++) {
				for (int z = -5; z <= 4; z++) {
					if (ClientTestHarness.level(server).getBlockState(new BlockPos(x, ClientTestHarness.TEST_Y, z))
						.is(Blocks.OAK_LOG)) {
						remaining++;
					}
				}
			}
			return remaining == 5;
		}, "Industrial chainsaw exceeded its 64-log traversal safety limit");
		test.screenshot(first, "tools-industrial-chainsaw-traversal-limit");
	}

	private static void testOmniToolMiningAndWrenchEnergy(ClientTestHarness test) {
		reset(test);
		BlockPos stone = new BlockPos(2, ClientTestHarness.TEST_Y, -1);
		BlockPos log = new BlockPos(2, ClientTestHarness.TEST_Y, 0);
		BlockPos dirt = new BlockPos(2, ClientTestHarness.TEST_Y, 1);
		test.setBlock(stone, Blocks.STONE);
		test.setBlock(log, Blocks.OAK_LOG);
		test.setBlock(dirt, Blocks.DIRT);
		test.setHotbarItem(charged(TRContent.OMNI_TOOL));
		test.runCommand("gamemode survival @a");
		test.mineBlockWithInput(stone, 25);
		test.mineBlockWithInput(log, 25);
		test.mineBlockWithInput(dirt, 25);
		test.assertServer(server -> ClientTestHarness.level(server).getBlockState(stone).isAir()
			&& ClientTestHarness.level(server).getBlockState(log).isAir()
			&& ClientTestHarness.level(server).getBlockState(dirt).isAir(),
			"Omni tool did not mine pickaxe, axe, and shovel materials");

		BlockPos machinePos = ClientTestHarness.INTERACTION_POS;
		test.setBlock(machinePos, TRContent.Machine.ELECTRIC_FURNACE.block);
		long[] before = new long[1];
		Direction[] facingBefore = new Direction[1];
		test.onServer(server -> {
			before[0] = energy(held(server));
			facingBefore[0] = ClientTestHarness.level(server).getBlockState(machinePos)
				.getValue(BlockMachineBase.FACING);
		});
		test.useBlockWithHeldItem(machinePos);
		test.assertServer(server -> energy(held(server)) == before[0] - 5,
			"Omni tool wrench interaction did not consume five energy");
		test.assertServer(server -> ClientTestHarness.requireBlockEntity(server, machinePos,
			MachineBaseBlockEntity.class).getBlockState().getValue(BlockMachineBase.FACING) != facingBefore[0],
			"Omni tool wrench interaction did not rotate the machine");
		test.screenshot(machinePos, "tools-omni-tool-mining-and-wrench");
	}

	private static void testRockCutterSilkTouch(ClientTestHarness test) {
		reset(test);
		BlockPos glass = ClientTestHarness.INTERACTION_POS;
		test.setBlock(glass, Blocks.GLASS);
		test.setCraftedHotbarItem(charged(TRContent.ROCK_CUTTER));
		test.assertServer(server -> !held(server).isEnchanted() && !held(server).hasFoil()
			&& held(server).isEnchantable(),
			"Crafted rock cutter should be unenchanted, without glint, and enchantable");
		test.runCommand("gamemode survival @a");
		test.screenshot(glass, "tools-rock-cutter-silk-touch-ready");
		test.mineBlockWithInput(glass, 35);
		test.assertServer(server -> server.getPlayerList().getPlayers().getFirst().getInventory()
			.contains(new ItemStack(Items.GLASS))
			|| ClientTestHarness.level(server).getEntitiesOfClass(ItemEntity.class,
				new net.minecraft.world.phys.AABB(glass).inflate(2)).stream()
				.anyMatch(entity -> entity.getItem().is(Items.GLASS)),
			"Rock cutter did not preserve glass with its built-in silk-touch behavior");
		test.assertServer(server -> !held(server).isEnchanted() && !held(server).hasFoil(),
			"Mining with a rock cutter changed the held item's enchantments");
	}

	private static void testRockCutterDropsAndEnchanting(ClientTestHarness test) {
		reset(test);
		test.runCommand("gamemode survival @a");
		test.onServer(server -> {
			var level = ClientTestHarness.level(server);
			var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
			var efficiency = enchantments.getOrThrow(Enchantments.EFFICIENCY);
			ItemStack cutter = charged(TRContent.ROCK_CUTTER);
			if (!cutter.isEnchantable() || EnchantmentHelper.getAvailableEnchantmentResults(30, cutter,
				Stream.of(efficiency)).isEmpty()) {
				throw new AssertionError("Rock cutter cannot receive Efficiency at an enchanting table");
			}

			var player = server.getPlayerList().getPlayers().getFirst();
			AnvilMenu anvil = new AnvilMenu(0, player.getInventory());
			anvil.getSlot(0).set(cutter.copy());
			anvil.getSlot(1).set(EnchantmentHelper.createBook(new EnchantmentInstance(efficiency, 3)));
			anvil.createResult();
			ItemStack enchanted = anvil.getSlot(anvil.getResultSlot()).getItem();
			if (enchanted.isEmpty() || EnchantmentHelper.getItemEnchantmentLevel(efficiency, enchanted) != 3) {
				throw new AssertionError("Rock cutter cannot receive Efficiency from a book at an anvil");
			}
			// Fortune must not bypass the cutter's block-preserving behavior.
			enchanted.enchant(enchantments.getOrThrow(Enchantments.FORTUNE), 3);
			for (Block block : new Block[]{Blocks.GLASS, Blocks.STONE, Blocks.ICE, Blocks.IRON_ORE,
				Blocks.DEEPSLATE_IRON_ORE, Blocks.COPPER_ORE, Blocks.GOLD_ORE, Blocks.DIAMOND_ORE,
				TRContent.Ores.TIN.block}) {
				for (ItemStack tool : new ItemStack[]{cutter, enchanted}) {
					var drops = Block.getDrops(block.defaultBlockState(), level, TARGET, null, player, tool);
					if (drops.size() != 1 || !drops.getFirst().is(block.asItem()) || drops.getFirst().getCount() != 1) {
						throw new AssertionError("Rock cutter did not preserve block " + block);
					}
				}
			}
			BlockPos icePos = TARGET.above(4);
			level.setBlockAndUpdate(icePos.below(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(icePos, Blocks.ICE.defaultBlockState());
			player.getInventory().setSelectedItem(cutter);
			if (!player.gameMode.destroyBlock(icePos) || !level.getBlockState(icePos).isAir()) {
				throw new AssertionError("Rock cutter turned mined ice into water");
			}
			if (energy(cutter) != energy(charged(TRContent.ROCK_CUTTER)) - techreborn.config.TechRebornConfig.rockCutterCost.get()) {
				throw new AssertionError("Rock cutter did not consume the configured energy when mining ice");
			}
			var normalDrops = Block.getDrops(Blocks.IRON_ORE.defaultBlockState(), level, TARGET, null,
				player, new ItemStack(Items.DIAMOND_PICKAXE));
			if (normalDrops.size() != 1 || !normalDrops.getFirst().is(Items.RAW_IRON)) {
				throw new AssertionError("Rock cutter behavior leaked into an ordinary pickaxe");
			}
			BlockPos xpPos = TARGET.above(5);
			AABB xpArea = new AABB(xpPos).inflate(2);
			int xpBefore = level.getEntitiesOfClass(ExperienceOrb.class, xpArea).size();
			Block.dropResources(Blocks.DIAMOND_ORE.defaultBlockState(), level, xpPos, null, player, cutter);
			if (level.getEntitiesOfClass(ExperienceOrb.class, xpArea).size() != xpBefore) {
				throw new AssertionError("Rock cutter generated ore experience while preserving the ore block");
			}
			if (cutter.isEnchanted() || cutter.hasFoil()
				|| EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.SILK_TOUCH), enchanted) != 0
				|| EnchantmentHelper.getItemEnchantmentLevel(efficiency, enchanted) != 3) {
				throw new AssertionError("Drop calculation changed the original tool's enchantments");
			}

			CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), false, level.registryAccess());
			int tabsWithCutter = 0;
			for (var tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
				var cutters = tab.getDisplayItems().stream().filter(stack -> stack.is(TRContent.ROCK_CUTTER)).toList();
				if (!cutters.isEmpty()) {
					tabsWithCutter++;
					if (cutters.stream().anyMatch(stack -> stack.isEnchanted() || stack.hasFoil() || !stack.isEnchantable())) {
						throw new AssertionError("Creative tab contains an enchanted rock cutter: " + tab.getDisplayName());
					}
				}
			}
			if (tabsWithCutter < 2) {
				throw new AssertionError("Rock cutter is missing from creative tabs");
			}
		});
	}

	private static void testElectricTreetap(ClientTestHarness test) {
		reset(test);
		BlockPos log = ClientTestHarness.INTERACTION_POS;
		test.onServer(server -> ClientTestHarness.level(server).setBlockAndUpdate(log,
			TRContent.RUBBER_LOG.defaultBlockState()
				.setValue(BlockRubberLog.HAS_SAP, true)
				.setValue(BlockRubberLog.SAP_SIDE, Direction.WEST)));
		ItemStack treetap = charged(TRContent.ELECTRIC_TREE_TAP);
		long initialEnergy = energy(treetap);
		test.setHotbarItem(treetap);
		test.runCommand("gamemode survival @a");
		test.useBlockWithHeldItem(log);
		test.assertServer(server -> server.getPlayerList().getPlayers().getFirst().getInventory()
			.contains(TRContent.Parts.SAP.getStack()), "Electric treetap did not collect sap from the exposed face");
		test.assertServer(server -> energy(held(server)) < initialEnergy,
			"Electric treetap did not consume energy while extracting sap");
		test.screenshot(log, "tools-electric-treetap-sap-extracted");
	}

	private static void fillWall(ClientTestHarness test, int radius, Block block) {
		for (int y = -1; y <= 1 + (radius - 1) * 2; y++) {
			for (int z = -radius; z <= radius; z++) {
				test.setBlock(TARGET.offset(0, y, z), block);
			}
		}
	}

	private static int countBlocks(net.minecraft.server.MinecraftServer server, Block block, int radius) {
		int count = 0;
		for (int y = -1; y <= 1 + (radius - 1) * 2; y++) {
			for (int z = -radius; z <= radius; z++) {
				if (ClientTestHarness.level(server).getBlockState(TARGET.offset(0, y, z)).is(block)) {
					count++;
				}
			}
		}
		return count;
	}

	private static void reset(ClientTestHarness test) {
		test.resetTestState();
	}
}
