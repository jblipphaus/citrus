/*
 * Copyright 2006-2010 ConSol* Software GmbH.
 * 
 * This file is part of Citrus.
 * 
 * Citrus is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Citrus is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Citrus. If not, see <http://www.gnu.org/licenses/>.
 */

package com.consol.citrus.functions.core;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.testng.annotations.Test;

import com.consol.citrus.exceptions.InvalidFunctionUsageException;

/**
 * Test the {@link RandomEnumValueFunction} function.
 * 
 * @author Dimo Velev (dimo.velev@gmail.com)
 *
 */
public class RandomEnumValueFunctionTest {
	private Random random = new Random(System.currentTimeMillis());
	
	private List<String> generateRandomValues() {
		final int valueCount = random.nextInt(15) + 5;
		final List<String> values = new ArrayList<String>(valueCount);
		for (int i=0; i<valueCount; i++) {
			values.add("value" + i);
		}
		return values;
	}
	
	@Test
	public void testWithParameters() {
		RandomEnumValueFunction testee = new RandomEnumValueFunction();
		final List<String> values = generateRandomValues();
		for(int i=0; i<100; i++) {
			final String value = testee.execute(values);
			assertNotNull(value);
			assertTrue(values.contains(value));
		}
	}
	
	@Test
	public void testWithValues() {
		RandomEnumValueFunction testee = new RandomEnumValueFunction();
		testee.setValues(generateRandomValues());
		final List<String> noParameters = Collections.emptyList();
		
		for(int i=0; i<100; i++) {
			final String value = testee.execute(noParameters);
			assertNotNull(value);
			assertTrue(testee.getValues().contains(value));
		}
	}
	
	@Test(expectedExceptions = {InvalidFunctionUsageException.class})
	public void testWithBoth() {
		RandomEnumValueFunction testee = new RandomEnumValueFunction();
		testee.setValues(generateRandomValues());
		final List<String> params = generateRandomValues();
		testee.execute(params);
	}
	
	@Test(expectedExceptions = {InvalidFunctionUsageException.class})
	public void testWithNone() {
		RandomEnumValueFunction testee = new RandomEnumValueFunction();
		final List<String> noParameters = Collections.emptyList();
		testee.execute(noParameters);
	}
}