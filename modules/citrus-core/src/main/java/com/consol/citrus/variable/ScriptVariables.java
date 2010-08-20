/*
 * Copyright 2006-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.consol.citrus.variable;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.springframework.beans.factory.BeanCreationException;

/**
 * Get variable values from scripts (e.g. Groovy)
 * 
 * @author Jan Lipphaus
 */
public class ScriptVariables {
	
	
	/**
	 * Evaluates and returns a script's result.
	 *
	 * @param engineName the name of the scripting engine
	 * @param script the script itself
	 * @return the evaluated value
	 */
	public static String getValue(String engineName, String script) {
		try {
			ScriptEngine engine = new ScriptEngineManager().getEngineByName(engineName);
			return engine.eval(script).toString();
		} catch (ScriptException e) {
			throw new BeanCreationException("Failed to evaluate " + engineName + " script.", e);
		} catch (NullPointerException e) {
			throw new BeanCreationException("\"" + engineName + "\" is no valid script engine name.", e);
		}
	}
}
