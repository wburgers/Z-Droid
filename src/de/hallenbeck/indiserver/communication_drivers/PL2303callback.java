/*
 *
 * This file is part of INDIserver.
 *
 * INDIserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * INDIserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with INDIserver.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2012 Alexander Tuschen <atuschen75 at gmail dot com>
 *
 */

package de.hallenbeck.indiserver.communication_drivers;

/**
 *  Callback Interface for pl2303 driver
 * @author atuschen75 at gmail dot com
 *
 */

public interface PL2303callback {
	/**
	 * Called after permission to device was granted and initialization was successful
	 */
	public void onInitSuccess();
	
	/**
	 * Called if permission to device was denied or initialization failed
	 */
	public void onInitFailed(String reason);
	
	/**
	 * Called if RI status line has changed
	 * @param state 
	 */
	public void onRI(boolean state);
	
	/**
	 * Called if DCD status line has changed
	 * @param state 
	 */
	public void onDCD(boolean state);
	
	/**
	 * Called if DSR status line has changed
	 * @param state 
	 */
	public void onDSR(boolean state);
	
	/**
	 * Called if CTS status line has changed
	 * @param state 
	 */
	public void onCTS(boolean state);
	
}
