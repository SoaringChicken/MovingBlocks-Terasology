/*
 * Copyright 2015 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.logic.behavior.asset;

import com.google.common.base.Charsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.asset.AssetLoader;
import org.terasology.logic.behavior.core.BehaviorNode;
import org.terasology.logic.behavior.core.BehaviorTreeBuilder;
import org.terasology.module.Module;
import org.terasology.registry.CoreRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.util.List;

/**
 * Loader for behavior assets. Can also save assets as json.
 * <p/>
 *
 * @author synopia
 */
public class BehaviorTreeLoader implements AssetLoader<BehaviorTreeData> {
    private static final Logger logger = LoggerFactory.getLogger(BehaviorTreeLoader.class);

    public void save(OutputStream stream, BehaviorTreeData data) throws IOException {
        BehaviorTreeBuilder builder = CoreRegistry.get(BehaviorTreeBuilder.class);
        OutputStreamWriter writer = new OutputStreamWriter(stream, Charsets.UTF_8);
        writer.write(builder.toJson(data.getRoot()));
        writer.close();
    }

    @Override
    public BehaviorTreeData load(Module module, InputStream stream, List<URL> urls, List<URL> deltas) throws IOException {
        BehaviorTreeBuilder builder = CoreRegistry.get(BehaviorTreeBuilder.class);
        if (builder == null) {
            builder = new BehaviorTreeBuilder();
            CoreRegistry.put(BehaviorTreeBuilder.class, builder);
        }
        BehaviorNode node = builder.fromJson(stream);

        BehaviorTreeData data = new BehaviorTreeData();
        data.setRoot(node);
        return data;
    }
}
