/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.conjure.java.services.dialogue;

import static java.util.stream.Collectors.toList;

import com.palantir.conjure.java.ConjureAnnotations;
import com.palantir.conjure.java.Options;
import com.palantir.conjure.java.services.ServiceGenerator;
import com.palantir.conjure.java.util.Packages;
import com.palantir.conjure.spec.EndpointDefinition;
import com.palantir.conjure.spec.ServiceDefinition;
import com.palantir.conjure.spec.Type;
import com.palantir.conjure.visitor.TypeVisitor;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.util.Optional;
import java.util.function.Function;
import javax.lang.model.element.Modifier;
import org.apache.commons.lang3.StringUtils;

public final class DialogueInterfaceGenerator {

    private final Options options;
    private final ParameterTypeMapper parameterTypes;
    private final ReturnTypeMapper returnTypes;

    public DialogueInterfaceGenerator(
            Options options, ParameterTypeMapper parameterTypes, ReturnTypeMapper returnTypes) {
        this.options = options;
        this.parameterTypes = parameterTypes;
        this.returnTypes = returnTypes;
    }

    public JavaFile generateBlocking(ServiceDefinition def, StaticFactoryMethodGenerator methodGenerator) {
        return generate(def, Names.blockingClassName(def, options), returnTypes::baseType, methodGenerator);
    }

    public JavaFile generateAsync(ServiceDefinition def, StaticFactoryMethodGenerator methodGenerator) {
        return generate(def, Names.asyncClassName(def, options), returnTypes::async, methodGenerator);
    }

    private JavaFile generate(
            ServiceDefinition def,
            ClassName className,
            Function<Optional<Type>, TypeName> returnTypeMapper,
            StaticFactoryMethodGenerator methodGenerator) {
        TypeSpec.Builder serviceBuilder = TypeSpec.interfaceBuilder(className)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(ConjureAnnotations.getConjureGeneratedAnnotation(DialogueInterfaceGenerator.class));

        def.getDocs().ifPresent(docs -> serviceBuilder.addJavadoc("$L", StringUtils.appendIfMissing(docs.get(), "\n")));

        serviceBuilder.addMethods(def.getEndpoints().stream()
                .map(endpoint -> apiMethod(endpoint, returnTypeMapper))
                .collect(toList()));

        serviceBuilder.addMethod(methodGenerator.generate(def));

        return JavaFile.builder(
                        Packages.getPrefixedPackage(def.getServiceName().getPackage(), options.packagePrefix()),
                        serviceBuilder.build())
                .build();
    }

    private MethodSpec apiMethod(EndpointDefinition endpointDef, Function<Optional<Type>, TypeName> returnTypeMapper) {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(
                        endpointDef.getEndpointName().get())
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .addParameters(parameterTypes.methodParams(endpointDef));
        endpointDef.getMarkers().forEach(marker -> {
            Preconditions.checkState(
                    marker.accept(TypeVisitor.IS_REFERENCE),
                    "Endpoint marker must be a reference type",
                    SafeArg.of("marker", marker));
            com.palantir.conjure.spec.TypeName referenceType = marker.accept(TypeVisitor.REFERENCE);
            methodBuilder.addAnnotation(ClassName.get(referenceType.getPackage(), referenceType.getName()));
        });

        endpointDef.getDeprecated().ifPresent(deprecatedDocsValue -> methodBuilder.addAnnotation(Deprecated.class));
        ServiceGenerator.getJavaDoc(endpointDef).ifPresent(content -> methodBuilder.addJavadoc("$L", content));

        methodBuilder.returns(returnTypeMapper.apply(endpointDef.getReturns()));

        return methodBuilder.build();
    }
}
