/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.items.contents;

import net.minecraft.item.ItemStack;
import appeng.parts.automation.UpgradeInventory;
import appeng.util.Platform;

public class CellUpgrades extends UpgradeInventory
{

	final ItemStack is;

	public CellUpgrades(ItemStack is, int upgrades) {
		super( is.getItem(), null, upgrades );
		this.is = is;
		readFromNBT( Platform.openNbtData( is ), "upgrades" );
	}

	@Override
	public void markDirty()
	{
		writeToNBT( Platform.openNbtData( is ), "upgrades" );
	}

}