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

package appeng.container.guisync;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.EnumSet;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.ICrafting;
import appeng.container.AEBaseContainer;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketProgressBar;
import appeng.core.sync.packets.PacketValueConfig;

public class SyncData
{

	private Object clientVersion;

	private final AEBaseContainer source;
	private final Field field;

	private final int channel;

	public SyncData(AEBaseContainer container, Field field, GuiSync annotation) {
		clientVersion = null;
		this.source = container;
		this.field = field;
		channel = annotation.value();
	}

	public int getChannel()
	{
		return channel;
	}

	public void tick(ICrafting c)
	{
		try
		{
			Object val = field.get( source );
			if ( val != null && clientVersion == null )
				send( c, val );
			else if ( !val.equals( clientVersion ) )
				send( c, val );
		}
		catch (IllegalArgumentException e)
		{
			AELog.error( e );
		}
		catch (IllegalAccessException e)
		{
			AELog.error( e );
		}
		catch (IOException e)
		{
			AELog.error( e );
		}
	}

	public void update(Object val)
	{
		try
		{
			Object oldValue = field.get( source );
			if ( val instanceof String )
				updateString( oldValue, (String) val );
			else
				updateValue( oldValue, (Long) val );
		}
		catch (IllegalArgumentException e)
		{
			AELog.error( e );
		}
		catch (IllegalAccessException e)
		{
			AELog.error( e );
		}

	}

	private void updateString(Object oldValue, String val)
	{
		try
		{
			field.set( source, val );
		}
		catch (IllegalArgumentException e)
		{
			AELog.error( e );
		}
		catch (IllegalAccessException e)
		{
			AELog.error( e );
		}
	}

	private void updateValue(Object oldValue, long val)
	{
		try
		{
			if ( field.getType().isEnum() )
			{
				EnumSet<? extends Enum> valList = EnumSet.allOf( (Class<? extends Enum>) field.getType() );
				for (Enum e : valList)
				{
					if ( e.ordinal() == val )
					{
						field.set( source, e );
						break;
					}
				}
			}
			else
			{
				if ( field.getType().equals( int.class ) )
					field.set( source, (int) val );
				else if ( field.getType().equals( long.class ) )
					field.set( source, val );
				else if ( field.getType().equals( boolean.class ) )
					field.set( source, val == 1 );
				else if ( field.getType().equals( double.class ) )
					field.set( source, Double.longBitsToDouble( val ) );
				else if ( field.getType().equals( Integer.class ) )
					field.set( source, (int) val );
				else if ( field.getType().equals( Long.class ) )
					field.set( source, val );
				else if ( field.getType().equals( Boolean.class ) )
					field.set( source, val == 1 );
				else if ( field.getType().equals( Double.class ) )
					field.set( source, Double.longBitsToDouble( val ) );
			}

			source.onUpdate( field.getName(), oldValue, field.get( source ) );
		}
		catch (IllegalArgumentException e)
		{
			AELog.error( e );
		}
		catch (IllegalAccessException e)
		{
			AELog.error( e );
		}
	}

	private void send(ICrafting o, Object val) throws IOException
	{
		if ( val instanceof String )
		{
			NetworkHandler.instance.sendTo( new PacketValueConfig( "SyncDat." + channel, (String) val ), (EntityPlayerMP) o );
		}
		else if ( field.getType().isEnum() )
		{
			o.sendProgressBarUpdate( source, channel, ((Enum) val).ordinal() );
		}
		else if ( val instanceof Long || val.getClass() == long.class )
		{
			NetworkHandler.instance.sendTo( new PacketProgressBar( channel, (Long) val ), (EntityPlayerMP) o );
		}
		else if ( val instanceof Boolean || val.getClass() == boolean.class )
		{
			o.sendProgressBarUpdate( source, channel, ((Boolean) val) ? 1 : 0 );
		}
		else if ( val instanceof Double || val.getClass() == double.class )
		{
			NetworkHandler.instance.sendTo( new PacketProgressBar( channel, Double.doubleToLongBits( (Double) val ) ), (EntityPlayerMP) o );
		}
		else
		{
			o.sendProgressBarUpdate( source, channel, (Integer) val );
		}

		clientVersion = val;
	}
}
