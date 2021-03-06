/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.models.sessions.infinispan.initializer;

import org.infinispan.Cache;
import org.infinispan.distexec.DistributedCallable;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.KeycloakSessionTask;
import org.keycloak.models.utils.KeycloakModelUtils;

import java.io.Serializable;
import java.util.Set;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class SessionInitializerWorker implements DistributedCallable<String, Serializable, InfinispanCacheInitializer.WorkerResult>, Serializable {

    private static final Logger log = Logger.getLogger(SessionInitializerWorker.class);

    private int segment;
    private SessionLoader.LoaderContext ctx;
    private SessionLoader sessionLoader;

    private transient Cache<String, Serializable> workCache;

    public void setWorkerEnvironment(int segment, SessionLoader.LoaderContext ctx, SessionLoader sessionLoader) {
        this.segment = segment;
        this.ctx = ctx;
        this.sessionLoader = sessionLoader;
    }

    @Override
    public void setEnvironment(Cache<String, Serializable> workCache, Set<String> inputKeys) {
        this.workCache = workCache;
    }

    @Override
    public InfinispanCacheInitializer.WorkerResult call() throws Exception {
        if (log.isTraceEnabled()) {
            log.tracef("Running computation for segment: %d", segment);
        }

        KeycloakSessionFactory sessionFactory = workCache.getAdvancedCache().getComponentRegistry().getComponent(KeycloakSessionFactory.class);
        if (sessionFactory == null) {
            log.debugf("KeycloakSessionFactory not yet set in cache. Worker skipped");
            return InfinispanCacheInitializer.WorkerResult.create(segment, false);
        }

        KeycloakModelUtils.runJobInTransaction(sessionFactory, new KeycloakSessionTask() {

            @Override
            public void run(KeycloakSession session) {
                sessionLoader.loadSessions(session, ctx, segment);
            }

        });

        return InfinispanCacheInitializer.WorkerResult.create(segment, true);
    }

}
