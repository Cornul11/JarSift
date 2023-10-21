/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.cornul11.maven;

import com.google.inject.Guice;
import org.eclipse.sisu.launch.Main;
import org.eclipse.sisu.space.BeanScanning;
import org.junit.Test;

public class MavenIndexTimestampLookupTest {
    @Test
    public void testApp() throws Exception {
        final com.google.inject.Module app = Main.wire(BeanScanning.INDEX);
        Guice.createInjector(app).getInstance(MavenIndexTimestampLookup.class).perform(new String[]{
                "org.codehaus.plexus",
                "plexus-interactivity-api",
                "1.0-alpha-4"}
        );
    }
}
