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

package org.jboss.as.logging;

import org.jboss.logmanager.Logger;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

import java.util.logging.Handler;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class LoggerHandlerService extends AbstractLoggerService {
    private final InjectedValue<Handler> handlerValue = new InjectedValue<Handler>();
    private Handler handler;

    protected LoggerHandlerService(final String name) {
        super(name);
    }

    protected void start(final StartContext context, final Logger logger) throws StartException {
        logger.addHandler(handler = handlerValue.getValue());
    }

    protected void stop(final StopContext context, final Logger logger) {
        try {
            logger.removeHandler(handler);
        } finally {
            handler = null;
        }
    }

    Injector<Handler> getHandlerInjector() {
        return handlerValue;
    }
}
