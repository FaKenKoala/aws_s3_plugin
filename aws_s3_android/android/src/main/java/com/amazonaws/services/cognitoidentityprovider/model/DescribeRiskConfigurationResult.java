/*
 * Copyright 2010-2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package com.amazonaws.services.cognitoidentityprovider.model;

import java.io.Serializable;

public class DescribeRiskConfigurationResult implements Serializable {
    /**
     * <p>
     * The risk configuration.
     * </p>
     */
    private RiskConfigurationType riskConfiguration;

    /**
     * <p>
     * The risk configuration.
     * </p>
     *
     * @return <p>
     *         The risk configuration.
     *         </p>
     */
    public RiskConfigurationType getRiskConfiguration() {
        return riskConfiguration;
    }

    /**
     * <p>
     * The risk configuration.
     * </p>
     *
     * @param riskConfiguration <p>
     *            The risk configuration.
     *            </p>
     */
    public void setRiskConfiguration(RiskConfigurationType riskConfiguration) {
        this.riskConfiguration = riskConfiguration;
    }

    /**
     * <p>
     * The risk configuration.
     * </p>
     * <p>
     * Returns a reference to this object so that method calls can be chained
     * together.
     *
     * @param riskConfiguration <p>
     *            The risk configuration.
     *            </p>
     * @return A reference to this updated object so that method calls can be
     *         chained together.
     */
    public DescribeRiskConfigurationResult withRiskConfiguration(
            RiskConfigurationType riskConfiguration) {
        this.riskConfiguration = riskConfiguration;
        return this;
    }

    /**
     * Returns a string representation of this object; useful for testing and
     * debugging.
     *
     * @return A string representation of this object.
     * @see Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        if (getRiskConfiguration() != null)
            sb.append("RiskConfiguration: " + getRiskConfiguration());
        sb.append("}");
        return sb.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int hashCode = 1;

        hashCode = prime * hashCode
                + ((getRiskConfiguration() == null) ? 0 : getRiskConfiguration().hashCode());
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;

        if (obj instanceof DescribeRiskConfigurationResult == false)
            return false;
        DescribeRiskConfigurationResult other = (DescribeRiskConfigurationResult) obj;

        if (other.getRiskConfiguration() == null ^ this.getRiskConfiguration() == null)
            return false;
        if (other.getRiskConfiguration() != null
                && other.getRiskConfiguration().equals(this.getRiskConfiguration()) == false)
            return false;
        return true;
    }
}