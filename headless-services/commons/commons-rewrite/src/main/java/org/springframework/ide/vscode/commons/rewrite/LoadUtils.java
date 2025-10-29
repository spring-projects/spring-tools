/*******************************************************************************
 * Copyright (c) 2022, 2023 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.rewrite;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import org.openrewrite.Recipe;
import org.openrewrite.config.DeclarativeRecipe;
import org.openrewrite.config.OptionDescriptor;
import org.openrewrite.config.RecipeDescriptor;
import org.openrewrite.config.RecipeIntrospectionException;
import org.openrewrite.internal.RecipeIntrospectionUtils;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class LoadUtils {

	public static class DurationTypeConverter implements JsonSerializer<Duration>, JsonDeserializer<Duration> {
		@Override
		public JsonElement serialize(Duration src, Type srcType, JsonSerializationContext context) {
			return new JsonPrimitive(src.toNanos());
		}
	
		@Override
		public Duration deserialize(JsonElement json, Type type, JsonDeserializationContext context)
				throws JsonParseException {
			return Duration.ofNanos(json.getAsLong());
		}
	}
	
	public static Recipe createRecipe(RecipeDescriptor d, Function<String, Class<? extends Recipe>> getRecipeClass) {
		return createRecipe(d, getRecipeClass, false);
	}
	
	public static Recipe createRecipe(RecipeDescriptor d, Function<String, Class<? extends Recipe>> getRecipeClass, boolean shallow) {
		Class<? extends Recipe> recipeClazz = getRecipeClass == null ? null : getRecipeClass.apply(d.getName());
		if (recipeClazz == null || DeclarativeRecipe.class.getName().equals(recipeClazz.getName())) {
			DeclarativeRecipe recipe = new DeclarativeRecipe(d.getName(), d.getDisplayName(), d.getDescription(),
					d.getTags(), d.getEstimatedEffortPerOccurrence(), null, false, d.getMaintainers());
			if (!shallow) {
				for (RecipeDescriptor subDescriptor : d.getRecipeList()) {
					recipe.getRecipeList().add(createRecipe(subDescriptor, getRecipeClass));
				}
			}
			return recipe;
		} else {
			return constructRecipe(recipeClazz, d.getOptions());
		}
	}
	
    public static Recipe constructRecipe(Class<?> recipeClass, List<OptionDescriptor> options) {
        Constructor<?> primaryConstructor = RecipeIntrospectionUtils.getZeroArgsConstructor(recipeClass);
        if (primaryConstructor == null) {
            primaryConstructor = RecipeIntrospectionUtils.getPrimaryConstructor(recipeClass);
        }
        Object[] constructorArgs = new Object[primaryConstructor.getParameterCount()];
        List<OptionDescriptor> remainingOptions = new ArrayList<>(options);
        for (int i = 0; i < primaryConstructor.getParameters().length; i++) {
            java.lang.reflect.Parameter param = primaryConstructor.getParameters()[i];
            int j = 0;
            for (; j < remainingOptions.size() && !param.getName().equals(remainingOptions.get(j).getName()); j++) {
            	// nothing
            }
            if (j < remainingOptions.size()) {
            	// found in option descriptors
            	OptionDescriptor optionDescriptor = remainingOptions.remove(j);
            	constructorArgs[i] = getOptionValueForType(param.getType(), optionDescriptor);
            } else {
            	// param not found in option descriptors
                if (param.getType().isPrimitive()) {
                    constructorArgs[i] = getPrimitiveDefault(param.getType());
                } else {
                    constructorArgs[i] = null;
                }
            }
        }
        primaryConstructor.setAccessible(true);
        try {
            Recipe recipe = (Recipe) primaryConstructor.newInstance(constructorArgs);
			for (OptionDescriptor option : remainingOptions) {
				if (option.getValue() != null) {
					Field f = recipe.getClass().getDeclaredField(option.getName());
					f.setAccessible(true);
					f.set(recipe, getOptionValueForType(f.getType(), option));
				}
			}
			return recipe;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchFieldException | SecurityException e) {
            // Should never happen
            throw new RecipeIntrospectionException("Unable to call primary constructor for Recipe " + recipeClass, e);
        }
    }
    
    private static Object getOptionValueForType(Class<?> type, OptionDescriptor option) {
    	if (option.getValue() instanceof Collection && type.isArray()) {
    		Collection<?> arrayValue = (Collection<?>) option.getValue();
    		Object[] valueToSet = (Object[]) Array.newInstance(type.getComponentType(), arrayValue.size());
    		int k = 0;
    		for (Object v : arrayValue) {
    			valueToSet[k] = getOptionValue(type.getComponentType(), v);
    		}
    		return valueToSet;
    	} else {
    		return getOptionValue(type, option.getValue());
    	}
    }
    
    private static Object getOptionValue(Class<?> type, Object v) {
    	if (v != null) {
        	if (type.equals(int.class) || type.equals(Integer.class)) {
        		return ((Number) v).intValue();
        	} else if (type.equals(long.class) || type.equals(Long.class)) {
        		return ((Number) v).longValue();
        	} else if (type.equals(short.class) || type.equals(Short.class)) {
        		return ((Number) v).shortValue();
        	} else if (type.equals(float.class) || type.equals(Float.class)) {
        		return ((Number) v).floatValue();
        	} else if (type.equals(double.class) || type.equals(Double.class)) {
        		return ((Number) v).shortValue();
        	} else if (Enum.class.isAssignableFrom(type) && v instanceof String) {
                try {
                    return type.getMethod("valueOf").invoke(v);
                } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
        	}
    	}
    	return v;
    }
    
    private static Object getPrimitiveDefault(Class<?> t) {
        if (t.equals(byte.class)) {
            return (byte) 0;
        } else if (t.equals(short.class)) {
            return (short) 0;
        } else if (t.equals(int.class)) {
            return 0;
        } else if (t.equals(long.class)) {
            return 0L;
        } else if (t.equals(float.class)) {
            return 0.0f;
        } else if (t.equals(double.class)) {
            return 0.0d;
        } else if (t.equals(char.class)) {
            return '\u0000';
        } else if (t.equals(boolean.class)) {
            return false;
        } else {
            throw new RecipeIntrospectionException(t.getCanonicalName() + " is not a supported primitive type");
        }
    }

}
