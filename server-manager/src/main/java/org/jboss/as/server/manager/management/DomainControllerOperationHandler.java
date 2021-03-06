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

package org.jboss.as.server.manager.management;

import java.io.DataOutput;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import org.jboss.as.domain.controller.DomainController;
import org.jboss.as.domain.controller.DomainControllerClient;
import org.jboss.as.server.manager.FileRepository;
import org.jboss.as.server.manager.RemoteDomainControllerClient;
import static org.jboss.as.server.manager.management.ManagementUtils.expectHeader;
import static org.jboss.as.server.manager.management.ManagementUtils.marshal;
import org.jboss.logging.Logger;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;


/**
 * {@link org.jboss.as.server.manager.management.ManagementOperationHandler} implementation used to handle request
 * intended for the domain controller.
 *
 * @author John Bailey
 */
public class DomainControllerOperationHandler implements ManagementOperationHandler, Service<DomainControllerOperationHandler> {
    private static final Logger log = Logger.getLogger("org.jboss.as.management");
    public static final ServiceName SERVICE_NAME = DomainController.SERVICE_NAME.append("operation", "handler");

    private final InjectedValue<DomainController> domainControllerValue = new InjectedValue<DomainController>();
    private final InjectedValue<ScheduledExecutorService> executorServiceValue = new InjectedValue<ScheduledExecutorService>();
    private final InjectedValue<FileRepository> localFileRepositoryValue = new InjectedValue<FileRepository>();

    private DomainController domainController;
    private ScheduledExecutorService executorService;
    private FileRepository localFileRepository;


    /** {@inheritDoc} */
    public final byte getIdentifier() {
        return ManagementProtocol.DOMAIN_CONTROLLER_REQUEST;
    }

    /** {@inheritDoc} */
    public synchronized void start(StartContext context) throws StartException {
        domainController = domainControllerValue.getValue();
        executorService = executorServiceValue.getValue();
        localFileRepository = localFileRepositoryValue.getValue();
    }

    /** {@inheritDoc} */
    public synchronized void stop(StopContext context) {
        domainController = null;
        executorService = null;
        localFileRepository = null;
    }

    /** {@inheritDoc} */
    public synchronized DomainControllerOperationHandler getValue() throws IllegalStateException {
        return this;
    }

    public Injector<DomainController> getDomainControllerInjector() {
        return domainControllerValue;
    }

    public Injector<ScheduledExecutorService> getExecutorServiceInjector() {
        return executorServiceValue;
    }

    public Injector<FileRepository> getLocalFileRepositoryInjector() {
        return localFileRepositoryValue;
    }

    /**
     * Handles the request.  Starts by reading the server manager id and proceeds to read the requested command byte.
     * Once the command is available it will get the appropriate operation and execute it.
     *
     * @param input  The operation input
     * @param output The operation output
     * @throws ManagementException If any problems occur performing the operation
     */
    public final void handleRequest(final int protocolVersion, final ByteDataInput input, final ByteDataOutput output) throws ManagementException {
        final byte commandCode;
        try {
            expectHeader(input, ManagementProtocol.REQUEST_OPERATION);
            commandCode = input.readByte();
        } catch (IOException e) {
            throw new ManagementException("ServerManager Request failed to read command code", e);
        }

        final ManagementOperation operation = operationFor(commandCode);
        if (operation == null) {
            throw new ManagementException("Invalid command code " + commandCode + " received from server manager");
        }
        try {
            log.debugf("Received DomainController operation [%s]", operation);
            operation.handle(input, output);
        } catch (Exception e) {
            throw new ManagementException("Failed to execute domain controller operation", e);
        }
    }

    private ManagementOperation operationFor(final byte commandByte) {
        switch (commandByte) {
            case ManagementProtocol.REGISTER_REQUEST:
                return new RegisterOperation();
            case ManagementProtocol.SYNC_FILE_REQUEST:
                return new GetFileOperation();
            case ManagementProtocol.UNREGISTER_REQUEST:
                return new UnregisterOperation();
            default: {
                return null;
            }
        }
    }

    private abstract class DomainControllerOperation extends ManagementResponse {
        protected void readRequest(final ByteDataInput input) throws ManagementException {
            super.readRequest(input);    //To change body of overridden methods use File | Settings | File Templates.
            final String serverManagerId;
            try {
                expectHeader(input, ManagementProtocol.PARAM_SERVER_MANAGER_ID);
                serverManagerId = input.readUTF();
                readRequest(serverManagerId, input);
            } catch (IOException e) {
                throw new ManagementException("ServerManager Request failed.  Unable to read signature", e);
            }
        }

        protected abstract void readRequest(final String serverManagerId, final ByteDataInput input) throws ManagementException;
    }

    private class RegisterOperation extends DomainControllerOperation {

        public final byte getRequestCode() {
            return ManagementProtocol.REGISTER_REQUEST;
        }

        protected final byte getResponseCode() {
            return ManagementProtocol.REGISTER_RESPONSE;
        }

        protected final void readRequest(final String serverManagerId, final ByteDataInput input) throws ManagementException {
            try {
                expectHeader(input, ManagementProtocol.PARAM_SERVER_MANAGER_HOST);
                final int addressSize = input.readInt();
                byte[] addressBytes = new byte[addressSize];
                input.readFully(addressBytes);
                expectHeader(input, ManagementProtocol.PARAM_SERVER_MANAGER_PORT);
                final int port = input.readInt();
                final InetAddress address = InetAddress.getByAddress(addressBytes);
                final DomainControllerClient client = new RemoteDomainControllerClient(serverManagerId, address, port, executorService);
                domainController.addClient(client);
                log.infof("Server manager registered [%s]", client);
            } catch (Exception e) {
                throw new ManagementException("Unable to read server manager connection information from request", e);
            }
        }

        protected final void sendResponse(final ByteDataOutput output) throws ManagementException {
            try {
                output.writeByte(ManagementProtocol.PARAM_DOMAIN_MODEL);
                marshal(output, domainController.getDomainModel());
            } catch (Exception e) {
                throw new ManagementException("Unable to write domain configuration to server manager", e);
            }
        }
    }

    private class UnregisterOperation extends DomainControllerOperation {
        public final byte getRequestCode() {
            return ManagementProtocol.UNREGISTER_REQUEST;
        }

        protected final byte getResponseCode() {
            return ManagementProtocol.UNREGISTER_RESPONSE;
        }

        protected final void readRequest(final String serverManagerId, final ByteDataInput input) throws ManagementException {
            log.infof("Server manager unregistered [%s]", serverManagerId);
            domainController.removeClient(serverManagerId);
        }
    }

    private class GetFileOperation extends DomainControllerOperation {
        private File localPath;

        public final byte getRequestCode() {
            return ManagementProtocol.SYNC_FILE_REQUEST;
        }

        protected final byte getResponseCode() {
            return ManagementProtocol.SYNC_FILE_RESPONSE;
        }

        protected final void readRequest(final String serverManagerId, final ByteDataInput input) throws ManagementException {
            final byte rootId;
            final String filePath;
            try {
                expectHeader(input, ManagementProtocol.PARAM_ROOT_ID);
                rootId = input.readByte();
                expectHeader(input, ManagementProtocol.PARAM_FILE_PATH);
                filePath = input.readUTF();
            } catch (Exception e) {
                throw new ManagementException("Unable to read file request attributes", e);
            }

            log.infof("Server manager [%s] requested file [%s] from root [%d]", serverManagerId, filePath, rootId);
            switch (rootId) {
                case 0: {
                    localPath = localFileRepository.getFile(filePath);
                    break;
                }
                case 1: {
                    localPath = localFileRepository.getConfigurationFile(filePath);
                    break;
                }
                case 2: {
                    localPath = localFileRepository.getDeploymentFile(filePath);
                    break;
                }
                default: {
                    localPath = null;
                }
            }
        }

        protected final void sendResponse(final ByteDataOutput output) throws ManagementException {
            try {
                output.writeByte(ManagementProtocol.PARAM_NUM_FILES);
                if (localPath == null || !localPath.exists()) {
                    output.writeInt(-1);
                } else if (localPath.isFile()) {
                    output.writeInt(1);
                    writeFile(localPath, output);
                } else {
                    final List<File> childFiles = getChildFiles(localPath);
                    output.writeInt(childFiles.size());
                    for (File child : childFiles) {
                        writeFile(child, output);
                    }
                }
            } catch (Exception e) {
                throw new ManagementException("Unable to write response to server manager", e);
            }
        }

        private List<File> getChildFiles(final File base) {
            final List<File> childFiles = new ArrayList<File>();
            getChildFiles(base, childFiles);
            return childFiles;
        }

        private void getChildFiles(final File base, final List<File> childFiles) {
            for(File child : base.listFiles()) {
                if(child.isFile()) {
                    childFiles.add(child);
                } else {
                    getChildFiles(child, childFiles);
                }
            }
        }

        private String getRelativePath(final File parent, final File child) {
            return child.getAbsolutePath().substring(parent.getAbsolutePath().length());
        }

        private void writeFile(final File file, final DataOutput output) throws IOException {
            output.writeByte(ManagementProtocol.FILE_START);
            output.writeByte(ManagementProtocol.PARAM_FILE_PATH);
            output.writeUTF(getRelativePath(localPath, file));
            output.writeByte(ManagementProtocol.PARAM_FILE_SIZE);
            output.writeLong(file.length());
            InputStream inputStream = null;
            try {
                inputStream = new FileInputStream(file);
                byte[] buffer = new byte[8192];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    output.write(buffer, 0, len);
                }
            } finally {
                if(inputStream != null) {
                    try {
                        inputStream.close();
                    } catch(IOException ignored){}
                }
            }
            output.writeByte(ManagementProtocol.FILE_END);
        }
    }
}
