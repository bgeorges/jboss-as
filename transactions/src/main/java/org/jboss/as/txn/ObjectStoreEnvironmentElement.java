/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.txn;

import org.jboss.as.model.AbstractModelElement;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

import javax.xml.stream.XMLStreamException;

/**
 * The model element for the object store environment element.
 *
 * @author John E. Bailey
 */
public class ObjectStoreEnvironmentElement extends AbstractModelElement<ObjectStoreEnvironmentElement> {
    private static final long serialVersionUID = 5036917797026753281L;
    private String directory = "/tmp/tx-object-store";

    protected ObjectStoreEnvironmentElement() throws XMLStreamException {
    }

    protected ObjectStoreEnvironmentElement(XMLExtendedStreamReader reader) throws XMLStreamException {
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i ++) {
            final String value = reader.getAttributeValue(i);
            if (reader.getAttributeNamespace(i) != null) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case DIRECTORY:
                        directory = value;
                        break;
                    default: unexpectedAttribute(reader, i);
                }
            }
        }
        // Handle elements
        requireNoContent(reader);
    }

    @Override
    public long elementHash() {
        return 0;
    }

    @Override
    protected Class<ObjectStoreEnvironmentElement> getElementClass() {
        return ObjectStoreEnvironmentElement.class;
    }

    @Override
    public void writeContent(XMLExtendedStreamWriter streamWriter) throws XMLStreamException {
        streamWriter.writeAttribute(Attribute.DIRECTORY.getLocalName(), directory);
        streamWriter.writeEndElement();
    }

    public String getDirectory() {
        return directory;
    }
}
