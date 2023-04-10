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

import java.util.Collections;
import java.util.Set;

public class ContainsValidator extends BaseJsonValidator {
    private static final Logger logger = LoggerFactory.getLogger(ContainsValidator.class);

    private int min = 1;
    private int max = Integer.MAX_VALUE;
    private final JsonSchema schema;
    private final String messageKeyMax = "contains.max";
    private final String messageKeyMin;

    public ContainsValidator(String schemaPath, JsonNode schemaNode, JsonSchema parentSchema, ValidationContext validationContext) {
        super(schemaPath, schemaNode, parentSchema, ValidatorTypeCode.CONTAINS, validationContext);

        // Draft 6 added the contains keyword but maxContains and minContains first
        // appeared in Draft 2019-09 so the semantics of the validation changes
        // slightly.
        VersionFlag version = SpecVersionDetector.detectOptionalVersion(parentSchema.getSchemaNode()).orElse(VersionFlag.V6);
        this.messageKeyMin = VersionFlag.V6 == version ? "contains" : "contains.min";

        if (schemaNode.isObject() || schemaNode.isBoolean()) {
            schema = new JsonSchema(validationContext, getValidatorType().getValue(), parentSchema.getCurrentUri(), schemaNode, parentSchema);

            JsonNode maxNode = parentSchema.getSchemaNode().get("maxContains");
            if (null != maxNode && maxNode.canConvertToExactIntegral()) {
                max = maxNode.intValue();
            }

            JsonNode minNode = parentSchema.getSchemaNode().get("minContains");
            if (null != minNode && minNode.canConvertToExactIntegral()) {
                min = minNode.intValue();
            }
        } else {
            schema = null;
        }

        parseErrorCode(getValidatorType().getErrorCodeKey());
    }

    public Set<ValidationMessage> validate(JsonNode node, JsonNode rootNode, String at) {
        debug(logger, node, rootNode, at);

        // ignores non-arrays
        if (null != schema && node.isArray()) {
            int actual = 0, i = 0;
            for (JsonNode n : node) {
                if (schema.validate(n, rootNode, atPath(at, i)).isEmpty()) {
                    ++actual;
                }
                ++i;
            }

            if (actual < min) {
                return boundsViolated(messageKeyMin, at, min);
            }

            if (actual > max) {
                return boundsViolated(messageKeyMax, at, max);
            }
        }

        return Collections.emptySet();
    }

    private Set<ValidationMessage> boundsViolated(String messageKey, String at, int bounds) {
        return Collections.singleton(constructValidationMessage(messageKey, at, "" + bounds, schema.getSchemaNode().toString()));
    }

    @Override
    public void preloadJsonSchema() {
        if (null != schema) {
            schema.initializeValidators();
        }
    }
}
