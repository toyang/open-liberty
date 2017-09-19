/*******************************************************************************
 * Copyright (c) 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.ws.microprofile.config.archaius.impl;

import java.lang.reflect.Type;
import java.util.Map;

import org.eclipse.microprofile.config.spi.Converter;

import com.ibm.ws.microprofile.config.impl.ConversionManager;
import com.netflix.archaius.api.Decoder;

/**
 *
 */
public class ConversionDecoder extends ConversionManager implements Decoder {

    /**
     * Constructor
     * 
     * @param converters
     */
    public ConversionDecoder(Map<Type, Converter<?>> converters) {
        super(converters);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override
    public <T> T decode(Class<T> type, String encoded) {
        return (T) convert(encoded, type);
    }

}