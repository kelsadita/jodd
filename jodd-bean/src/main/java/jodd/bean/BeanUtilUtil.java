// Copyright (c) 2003-present, Jodd Team (jodd.org). All Rights Reserved.

package jodd.bean;

import jodd.introspector.Getter;
import jodd.introspector.Introspector;
import jodd.introspector.Setter;
import jodd.typeconverter.TypeConverterManager;
import jodd.typeconverter.TypeConverterManagerBean;
import jodd.util.ReflectUtil;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.List;

/**
 * Various bean property utilities that makes writings of {@link BeanUtil} classes easy.
 */
class BeanUtilUtil {

	protected Introspector introspector = JoddBean.introspector;
	protected TypeConverterManagerBean typeConverterManager = TypeConverterManager.getDefaultTypeConverterManager();

	/**
	 * Sets {@link Introspector introspector} implementation.
	 */
	public void setIntrospector(Introspector introspector) {
		this.introspector = introspector;
	}

	/**
	 * Returns {@link Introspector introspector} implementation.
	 */
	public Introspector getIntrospector() {
		return introspector;
	}

	/**
	 * Sets {@link TypeConverterManagerBean type converter manager} implementation.
	 */
	public void setTypeConverterManager(TypeConverterManagerBean typeConverterManager) {
		this.typeConverterManager = typeConverterManager;
	}

	/**
	 * Returns {@link TypeConverterManagerBean type converter manager} implementation.
	 */
	public TypeConverterManagerBean getTypeConverterManager() {
		return typeConverterManager;
	}

	/**
	 * Converts object to destination type. Invoked before the
	 * value is set into destination. Throws <code>TypeConversionException</code>
	 * if conversion fails.
	 */
	@SuppressWarnings("unchecked")
	protected Object convertType(Object value, Class type) {
		return typeConverterManager.convertType(value, type);
	}

	/**
	 * Convert to collection.
	 */
	protected Object convertToCollection(Object value, Class destinationType, Class componentType) {
		return typeConverterManager.convertToCollection(value, destinationType, componentType);
	}

	
	// ---------------------------------------------------------------- accessors

	/**
	 * Invokes setter, but first converts type to match the setter type.
	 */
	protected Object invokeSetter(Setter setter, BeanProperty bp, Object value) {
		try {
			Class type = setter.getSetterRawType();

			if (ReflectUtil.isTypeOf(type, Collection.class)) {
				Class componentType = setter.getSetterRawComponentType();

				value = convertToCollection(value, type, componentType);
			} else {
				// no collections
				value = convertType(value, type);
			}

			setter.invokeSetter(bp.bean, value);
		} catch (Exception ex) {
			if (bp.silent) {
				return null;
			}
			throw new BeanException("Setter failed: " + setter, ex);
		}
		return value;
	}

	// ---------------------------------------------------------------- forced

	/**
	 * Returns the element of an array forced. If value is <code>null</code>, it will be instantiated.
	 * If not the last part of indexed bean property, array will be expanded to the index if necessary.
	 */
	protected Object arrayForcedGet(BeanProperty bp, Object array, int index) {
		Class componentType = array.getClass().getComponentType();
		if (bp.last == false) {
			array = ensureArraySize(bp, array, componentType, index);
		}
		Object value = Array.get(array, index);
		if (value == null) {
			try {
				value = ReflectUtil.newInstance(componentType);
			} catch (Exception ex) {
				if (bp.silent) {
					return null;
				}
				throw new BeanException("Invalid array element: " + bp.name + '[' + index + ']', bp, ex);
			}
			Array.set(array, index, value);
		}
		return value;
	}

	/**
	 * Sets the array element forced. If index is greater then arrays length, array will be expanded to the index.
	 * If speed is critical, it is better to allocate an array with proper size before using this method. 
	 */
	protected void arrayForcedSet(BeanProperty bp, Object array, int index, Object value) {
		Class componentType = array.getClass().getComponentType();
		array = ensureArraySize(bp, array, componentType, index);
		value = convertType(value, componentType);
		Array.set(array, index, value);
	}


	@SuppressWarnings({"SuspiciousSystemArraycopy"})
	protected Object ensureArraySize(BeanProperty bp, Object array, Class componentType, int index) {
		int len = Array.getLength(array);
		if (index >= len) {
			Object newArray = Array.newInstance(componentType, index + 1);
			System.arraycopy(array, 0, newArray, 0, len);

			Setter setter = bp.getSetter(true);
			if (setter == null) {
				// no point to check for bp.silent, throws NPE later
				throw new BeanException("Setter or field not found: " + bp.name, bp);
			}

			newArray = invokeSetter(setter, bp, newArray);

			array = newArray;
		}
		return array;
	}

	@SuppressWarnings({"unchecked"})
	protected void ensureListSize(List list, int size) {
		int len = list.size();
		while (size >= len) {
			list.add(null);
			len++;
		}
	}

	// ---------------------------------------------------------------- index

	/**
	 * Finds the very first next dot. Ignores dots between index brackets.
	 * Returns <code>-1</code> when dot is not found.
	 */
	protected int indexOfDot(String name) {
		int ndx = 0;
		int len = name.length();

		boolean insideBracket = false;

		while (ndx < len) {
			char c = name.charAt(ndx);

			if (insideBracket) {
				if (c == ']') {
					insideBracket = false;
				}
			} else {
				if (c == '.') {
					return ndx;
				}
				if (c == '[') {
					insideBracket = true;
				}
			}
			ndx++;
		}
		return -1;
	}


	/**
	 * Extract index string from non-nested property name.
	 * If index is found, it is stripped from bean property name.
	 * If no index is found, it returns <code>null</code>.
	 */
	protected String extractIndex(BeanProperty bp) {
		bp.index = null;
		String name = bp.name;
		int lastNdx = name.length() - 1;
		if (lastNdx < 0) {
			return null;
		}
		if (name.charAt(lastNdx) == ']') {
			int leftBracketNdx = name.lastIndexOf('[');
			if (leftBracketNdx != -1) {
				bp.setName(name.substring(0, leftBracketNdx));
				bp.index = name.substring(leftBracketNdx + 1, lastNdx);
				return bp.index;
			}
		}
		return null;
	}

	protected int parseInt(String indexString, BeanProperty bp) {
		try {
			return Integer.parseInt(indexString);
		} catch (NumberFormatException nfex) {
			// no point to use bp.silent, as will throw exception
			throw new BeanException("Invalid index: " + indexString, bp, nfex);
		}
	}

	// ---------------------------------------------------------------- create property

	/**
	 * Creates new instance for current property name through its setter.
	 * It uses default constructor!
	 */
	protected Object createBeanProperty(BeanProperty bp) {
		Setter setter = bp.getSetter(true);
		if (setter == null) {
			return null;
		}

		Class type = setter.getSetterRawType();

		Object newInstance;
		try {
			newInstance = ReflectUtil.newInstance(type);
		} catch (Exception ex) {
			if (bp.silent) {
				return null;
			}
			throw new BeanException("Invalid property: " + bp.name, bp, ex);
		}

		newInstance = invokeSetter(setter, bp, newInstance);

		return newInstance;
	}

	// ---------------------------------------------------------------- generic and type

	/**
	 * Extracts generic component type of a property. Returns <code>Object.class</code>
	 * when property does not have component.
	 */
	protected Class extractGenericComponentType(Getter getter) {
		Class componentType = null;

		if (getter != null) {
			componentType = getter.getGetterRawComponentType();
		}

		if (componentType == null) {
			componentType = Object.class;
		}
		return componentType;
	}

	/**
	 * Converts <b>Map</b> index to key type. If conversion fails, original value will be returned.
	 */
	protected Object convertIndexToMapKey(Getter getter, Object index) {
		Class indexType = null;

		if (getter != null) {
			indexType = getter.getGetterRawKeyComponentType();
		}

		// check if set
		if (indexType == null) {
			indexType = Object.class;	// marker for no generic type
		}

		if (indexType == Object.class) {
			return index;
		}

		try {
			return convertType(index, indexType);
		} catch (Exception ignore) {
			return index;
		}
	}

	/**
	 * Extracts type of current property.
	 */
	protected Class extractType(BeanProperty bp) {
		Getter getter = bp.getGetter(bp.declared);
		if (getter != null) {
			if (bp.index != null) {
				Class type = getter.getGetterRawComponentType();
				return type == null ? Object.class : type;
			}
			return getter.getGetterRawType();
		}

		return null;	// this should not happens
	}

}