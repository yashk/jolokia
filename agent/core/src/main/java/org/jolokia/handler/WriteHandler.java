package org.jolokia.handler;

import org.jolokia.request.*;
import org.jolokia.restrictor.Restrictor;
import org.jolokia.converter.json.ObjectToJsonConverter;
import org.jolokia.util.RequestType;

import javax.management.*;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

/*
 *  Copyright 2009-2010 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */


/**
 * Handler for dealing with write request.
 *
 * @author roland
 * @since Jun 12, 2009
 */
public class WriteHandler extends JsonRequestHandler<JmxWriteRequest> {

    private ObjectToJsonConverter objectToJsonConverter;

    public WriteHandler(Restrictor pRestrictor, ObjectToJsonConverter pObjectToJsonConverter) {
        super(pRestrictor);
        objectToJsonConverter = pObjectToJsonConverter;
    }

    @Override
    public RequestType getType() {
        return RequestType.WRITE;
    }

    @Override
    protected void checkForRestriction(JmxWriteRequest pRequest) {
        if (!getRestrictor().isAttributeWriteAllowed(pRequest.getObjectName(),pRequest.getAttributeName())) {
            throw new SecurityException("Writing attribute " + pRequest.getAttributeName() +
                    " forbidden for MBean " + pRequest.getObjectNameAsString());
        }
    }

    @Override
    public Object doHandleRequest(MBeanServerConnection server, JmxWriteRequest request)
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException {

        try {
            return setAttribute(request, server);
        } catch (IntrospectionException exp) {
            throw new IllegalArgumentException("Cannot get info for MBean " + request.getObjectName() + ": " +exp,exp);
        } catch (InvalidAttributeValueException e) {
            throw new IllegalArgumentException("Invalid value " + request.getValue() + " for attribute " +
                    request.getAttributeName() + ", MBean " + request.getObjectNameAsString(),e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Cannot set value " + request.getValue() + " for attribute " +
                    request.getAttributeName() + ", MBean " + request.getObjectNameAsString(),e);
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException("Cannot set value " + request.getValue() + " for attribute " +
                    request.getAttributeName() + ", MBean " + request.getObjectNameAsString(),e);
        }
    }

    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "NP_NULL_ON_SOME_PATH", justification = "aInfo below cant be null. See comment inline.")
    private Object setAttribute(JmxWriteRequest request, MBeanServerConnection server)
            throws MBeanException, AttributeNotFoundException, InstanceNotFoundException,
            ReflectionException, IntrospectionException, InvalidAttributeValueException, IllegalAccessException, InvocationTargetException, IOException {
        // Old value, will throw an exception if attribute is not known. That's good.
        Object oldValue = server.getAttribute(request.getObjectName(), request.getAttributeName());

        MBeanInfo mInfo = server.getMBeanInfo(request.getObjectName());
        MBeanAttributeInfo aInfo = null;
        
        for (MBeanAttributeInfo i : mInfo.getAttributes()) {
            if (i.getName().equals(request.getAttributeName())) {
                aInfo = i;
                break;
            }
        }
        // aInfo is != null otherwise getAttribute() would have already thrown an ArgumentNotFoundException
        String type = aInfo.getType();
        Object[] values = objectToJsonConverter.getValues(type,oldValue,request);
        Attribute attribute = new Attribute(request.getAttributeName(),values[0]);
        server.setAttribute(request.getObjectName(),attribute);
        return values[1];
    }

    /**
     * The old value is returned directly, hence we do not want any path conversion
     * on this value
     *
     * @return false;
     */
    @Override
    public boolean useReturnValueWithPath() {
        return false;
    }
}

