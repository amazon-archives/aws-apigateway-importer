/*
 * Copyright 2010-2015 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.amazonaws.service.apigateway.importer.util;

import com.amazonaws.services.apigateway.model.PatchDocument;
import com.amazonaws.services.apigateway.model.PatchOperation;

import java.util.ArrayList;

public class PatchUtils {

    public static PatchOperation createReplaceOperation(String path, String value) {
        PatchOperation op = new PatchOperation();
        op.setOp("replace");
        op.setPath(path);
        op.setValue(value);
        return op;
    }

    public static  PatchOperation createRemoveOperation(String path) {
        PatchOperation op = new PatchOperation();
        op.setOp("remove");
        op.setPath(path);
        return op;
    }

    public static  PatchOperation createAddOperation(String path, String value) {
        PatchOperation op = new PatchOperation();
        op.setOp("add");
        op.setPath(path);
        op.setValue(value);
        return op;
    }

    public static  PatchDocument createPatchDocument(PatchOperation... ops) {
        PatchDocument pd = new PatchDocument();
        pd.setPatchOperations(new ArrayList<>());
        for (PatchOperation op : ops) {
            pd.getPatchOperations().add(op);
        }
        return pd;
    }
}
