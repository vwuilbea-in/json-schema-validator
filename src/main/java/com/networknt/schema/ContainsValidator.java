/*
 * Copyright (c) 2016 Network New Technologies Inc.
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

package com.networknt.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.SpecVersion.VersionFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static com.networknt.schema.VersionCode.MinV201909;

public class ContainsValidator extends BaseJsonValidator {
    private static final Logger logger = LoggerFactory.getLogger(ContainsValidator.class);
    private static final String CONTAINS_MAX = "contains.max";
    private static final String CONTAINS_MIN = "contains.min";
    private static final VersionFlag DEFAULT_VERSION = VersionFlag.V6;

    private final JsonSchema schema;
    private final boolean isMinV201909;

    private int min = 1;
    private int max = Integer.MAX_VALUE;

    public ContainsValidator(String schemaPath, JsonNode schemaNode, JsonSchema parentSchema, ValidationContext validationContext) {
        super(schemaPath, schemaNode, parentSchema, ValidatorTypeCode.CONTAINS, validationContext);

        // Draft 6 added the contains keyword but maxContains and minContains first
        // appeared in Draft 2019-09 so the semantics of the validation changes
        // slightly.
        isMinV201909 = MinV201909.getVersions().contains(SpecVersionDetector.detectOptionalVersion(validationContext.getMetaSchema().getUri()).orElse(DEFAULT_VERSION));

        if (schemaNode.isObject() || schemaNode.isBoolean()) {
            this.schema = new JsonSchema(validationContext, getValidatorType().getValue(), parentSchema.getCurrentUri(), schemaNode, parentSchema);

            JsonNode parentSchemaNode = parentSchema.getSchemaNode();
            Optional.ofNullable(parentSchemaNode.get(ValidatorTypeCode.MAX_CONTAINS.getValue()))
                    .filter(JsonNode::canConvertToExactIntegral)
                    .ifPresent(node -> this.max = node.intValue());

            Optional.ofNullable(parentSchemaNode.get(ValidatorTypeCode.MIN_CONTAINS.getValue()))
                    .filter(JsonNode::canConvertToExactIntegral)
                    .ifPresent(node -> this.min = node.intValue());
        } else {
            this.schema = null;
        }

        parseErrorCode(getValidatorType().getErrorCodeKey());
    }

    @Override
    public Set<ValidationMessage> validate(JsonNode node, JsonNode rootNode, String at) {
        debug(logger, node, rootNode, at);

        // ignores non-arrays
        if (null != this.schema && node.isArray()) {
            Collection<String> evaluatedItems = CollectorContext.getInstance().getEvaluatedItems();

            int actual = 0, i = 0;
            for (JsonNode n : node) {
                String path = atPath(at, i);

                if (this.schema.validate(n, rootNode, path).isEmpty()) {
                    ++actual;
                    evaluatedItems.add(path);
                }
                ++i;
            }

            if (actual < this.min) {
                if(isMinV201909) {
                    updateValidatorType(ValidatorTypeCode.MIN_CONTAINS);
                }
                return boundsViolated(isMinV201909 ? CONTAINS_MIN : ValidatorTypeCode.CONTAINS.getValue(), at, this.min);
            }

            if (actual > this.max) {
                if(isMinV201909) {
                    updateValidatorType(ValidatorTypeCode.MAX_CONTAINS);
                }
                return boundsViolated(isMinV201909 ? CONTAINS_MAX : ValidatorTypeCode.CONTAINS.getValue(), at, this.max);
            }
        }

        return Collections.emptySet();
    }

    @Override
    public void preloadJsonSchema() {
        Optional.ofNullable(this.schema).ifPresent(JsonSchema::initializeValidators);
    }

    private Set<ValidationMessage> boundsViolated(String messageKey, String at, int bounds) {
        return Collections.singleton(constructValidationMessage(messageKey, at, String.valueOf(bounds), this.schema.getSchemaNode().toString()));
    }
}
