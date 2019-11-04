/*
 * Copyright 2018 Vizor Games LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.vizor.unreal.convert;

import com.google.common.collect.ImmutableList;
import com.squareup.wire.schema.internal.parser.MessageElement;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.vizor.unreal.config.Config;
import com.vizor.unreal.preprocess.NestedTypesRemover;
import com.vizor.unreal.preprocess.PackageTypeRemover;
import com.vizor.unreal.preprocess.Preprocessor;
import com.vizor.unreal.proto.Linker;
import com.vizor.unreal.proto.ParseResult;
import com.vizor.unreal.util.ImportsResolver;
import com.vizor.unreal.util.Tuple;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.squareup.wire.schema.Location.get;
import static com.squareup.wire.schema.internal.parser.ProtoParser.parse;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.apache.logging.log4j.LogManager.getLogger;

public class Converter
{
    private static final Logger log = getLogger(Converter.class);

    @SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
    private static final List<Class<? extends Preprocessor>> preprocessorClasses = asList(
            PackageTypeRemover.class,
            NestedTypesRemover.class

        // Add new ones if you want to...
    );

    private final String moduleName;

    public Converter(final String moduleName)
    {
        this.moduleName = moduleName;
    }



    public void convert(final Path srcPath, final List<Tuple<Path, Path>> paths)
    {
        List<Path> protoFiles = new ArrayList<>();
        paths.forEach(tuple -> protoFiles.add(tuple.first()));

        final ParseResult parseResult = new ParseResult(protoFiles);


        Stream<Tuple<Path, Path>> pathStream = paths.stream();

        // Mark
        if (!Config.get().isNoFork())
            pathStream = pathStream.parallel();

        parseResult.getElements().stream().forEach(protoFileElement -> {
            convert(srcPath, protoFileElement, srcPath);
        });


    }

    private void convert(final Path srcPath, final ProtoFileElement parse, final Path pathToConverted)
    {
        final ImmutableList<String> publicImports = parse.publicImports();
        final ImmutableList<String> privateImports = parse.imports();

        final ArrayList<String> allImports = new ArrayList<>();
        allImports.addAll(publicImports);
        allImports.addAll(privateImports);

        parse.services().forEach(serviceElement -> {
            serviceElement.rpcs().forEach(rpcElement -> {
                final String requestType = rpcElement.requestType();
                final String responseType = rpcElement.responseType();

                if(requestType.contains("."))
                {
                    final String[] split = requestType.split(".");
                    final String packageName = split[0];
                    final String importedSymbol = split[1];


                }
            });
        });

        final List<ProtoFileElement> elements = preProcess(parse);
        log.debug("Done parsing {}", parse.location());
        log.debug("Processing {}", parse.location());

        elements.forEach(e -> {
            final Path relativePath = srcPath.relativize(new File(parse.location().toString()).toPath());
            new ProtoProcessor(e, relativePath, pathToConverted, moduleName).run();
        });

        //log.debug("Done processing {}", pathToProto);
    }

    private List<ProtoFileElement> preProcess(ProtoFileElement element)
    {
        final List<ProtoFileElement> elements = new ArrayList<>();
        elements.add(element);

        try
        {
            for (final Class<? extends Preprocessor> c : preprocessorClasses)
            {
                final Preprocessor p = c.cast(c.newInstance());

                // note that each processor outputs a set of ProtoFileElements's
                // which should be processed independent of each other.
                final List<ProtoFileElement> processed = elements.stream()
                    .peek(e -> log.debug("Processing '{}' with '{}'", e.packageName(), p.getClass().getSimpleName()))
                    .map(p::process)
                    .collect(toList());

                elements.clear();
                elements.addAll(processed);
            }
        }
        catch (Throwable e)
        {
            throw new RuntimeException(e);
        }

        return elements;
    }
}
