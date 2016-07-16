/* Copyright (C) 2016  Matteo Hausner
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package de.bwravencl.controllerbuddy.input.action;

import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

public interface ISuspendableAction extends IAction {

	static final long SUSPEND_TIME = 500L;

	static final Set<ISuspendableAction> suspendedActions = ConcurrentHashMap.newKeySet();

	default boolean isSuspended() {
		return suspendedActions.contains(this);
	}

	public default void suspend() {
		suspendedActions.remove(this);
		suspendedActions.add(this);

		new Timer().schedule(new TimerTask() {

			@Override
			public void run() {
				suspendedActions.remove(ISuspendableAction.this);
			}

		}, SUSPEND_TIME);
	}

}