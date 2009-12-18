/*
 * Copyright 2006-2009 ConSol* Software GmbH.
 * 
 * This file is part of Citrus.
 * 
 *  Citrus is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Citrus is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Citrus.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.consol.citrus.actions;

import org.testng.annotations.Test;

import com.consol.citrus.testng.AbstractBaseTest;

public class SleepActionTest extends AbstractBaseTest {
	
	@Test
	public void testSleep() {
		SleepAction sleep = new SleepAction();
		
		sleep.setDelay("0.1");
		sleep.execute(context);
	}
	
	@Test
    public void testSleepVariablesSupport() {
        SleepAction sleep = new SleepAction();
        
        context.setVariable("time", "0.1");
        sleep.setDelay("${time}");
        
        sleep.execute(context);
    }
}
