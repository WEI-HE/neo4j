/*
 * Copyright (c) 2002-2018 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.index.schema;

import java.io.File;
import java.io.IOException;

import org.neo4j.gis.spatial.index.curves.SpaceFillingCurveConfiguration;
import org.neo4j.index.internal.gbptree.GBPTree;
import org.neo4j.index.internal.gbptree.RecoveryCleanupWorkCollector;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.pagecache.IOLimiter;
import org.neo4j.io.pagecache.PageCache;
import org.neo4j.kernel.api.index.IndexProvider;
import org.neo4j.kernel.impl.api.index.sampling.IndexSamplingConfig;
import org.neo4j.kernel.impl.index.schema.config.IndexSpecificSpaceFillingCurveSettingsCache;
import org.neo4j.kernel.impl.index.schema.config.SpaceFillingCurveSettingsWriter;
import org.neo4j.kernel.impl.util.Validator;
import org.neo4j.storageengine.api.schema.IndexReader;
import org.neo4j.storageengine.api.schema.StoreIndexDescriptor;
import org.neo4j.values.storable.Value;

class GenericNativeIndexAccessor extends NativeIndexAccessor<CompositeGenericKey,NativeIndexValue>
{
    private final IndexSpecificSpaceFillingCurveSettingsCache spaceFillingCurveSettings;
    private final SpaceFillingCurveConfiguration configuration;
    private Validator<Value[]> validator;

    GenericNativeIndexAccessor( PageCache pageCache, FileSystemAbstraction fs, File storeFile, IndexLayout<CompositeGenericKey,NativeIndexValue> layout,
            RecoveryCleanupWorkCollector recoveryCleanupWorkCollector, IndexProvider.Monitor monitor, StoreIndexDescriptor descriptor,
            IndexSamplingConfig samplingConfig, IndexSpecificSpaceFillingCurveSettingsCache spaceFillingCurveSettings,
            SpaceFillingCurveConfiguration configuration ) throws IOException
    {
        super( pageCache, fs, storeFile, layout, recoveryCleanupWorkCollector, monitor, descriptor, samplingConfig,
                new SpaceFillingCurveSettingsWriter( spaceFillingCurveSettings ) );
        this.spaceFillingCurveSettings = spaceFillingCurveSettings;
        this.configuration = configuration;
        instantiateTree( recoveryCleanupWorkCollector, headerWriter );
    }

    @Override
    protected void afterTreeInstantiation( GBPTree<CompositeGenericKey,NativeIndexValue> tree )
    {
        validator = new GenericIndexKeyValidator( tree.keyValueSizeCap(), layout, spaceFillingCurveSettings, pageCache.pageSize() );
    }

    @Override
    public IndexReader newReader()
    {
        return new GenericNativeIndexReader( tree, layout, samplingConfig, descriptor, spaceFillingCurveSettings, configuration );
    }

    @Override
    public void validateBeforeCommit( Value[] tuple )
    {
        validator.validate( tuple );
    }

    @Override
    public void force( IOLimiter ioLimiter )
    {
        // This accessor needs to use the header writer here because coordinate reference systems may have changed since last checkpoint.
        tree.checkpoint( ioLimiter, headerWriter );
    }
}
