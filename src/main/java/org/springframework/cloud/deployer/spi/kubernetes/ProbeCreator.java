/*
 * Copyright 2018 the original author or authors.
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

package org.springframework.cloud.deployer.spi.kubernetes;

import io.fabric8.kubernetes.api.model.HTTPGetActionBuilder;
import io.fabric8.kubernetes.api.model.HTTPHeader;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base class for creating Probe's
 *
 * @author Chris Schaefer
 */
abstract class ProbeCreator {
	protected static final String KUBERNETES_DEPLOYER_PREFIX = "spring.cloud.deployer.kubernetes";
	protected static final String AUTHORIZATION_HEADER_NAME = "Authorization";
	protected static final String PROBE_CREDENTIALS_SECRET_KEY_NAME = "credentials";
	protected static final String BOOT_1_READINESS_PROBE_PATH = "/info";
	protected static final String BOOT_2_READINESS_PROBE_PATH = "/actuator" + BOOT_1_READINESS_PROBE_PATH;
	protected static final String BOOT_1_LIVENESS_PROBE_PATH = "/health";
	protected static final String BOOT_2_LIVENESS_PROBE_PATH = "/actuator" + BOOT_1_LIVENESS_PROBE_PATH;
	private static final int BOOT_2_MAJOR_VERSION = 2;

	private ContainerConfiguration containerConfiguration;

	private KubernetesDeployerProperties kubernetesDeployerProperties;

	ProbeCreator(KubernetesDeployerProperties kubernetesDeployerProperties,
			ContainerConfiguration containerConfiguration) {
		this.containerConfiguration = containerConfiguration;
		this.kubernetesDeployerProperties = kubernetesDeployerProperties;
	}

	Probe create() {
		HTTPGetActionBuilder httpGetActionBuilder = new HTTPGetActionBuilder()
				.withPath(getProbePath())
				.withNewPort(getPort());

		List<HTTPHeader> httpHeaders = getHttpHeaders();

		if (!httpHeaders.isEmpty()) {
			httpGetActionBuilder.withHttpHeaders(httpHeaders);
		}

		return new ProbeBuilder()
				.withHttpGet(httpGetActionBuilder.build())
				.withTimeoutSeconds(getTimeout())
				.withInitialDelaySeconds(getInitialDelay())
				.withPeriodSeconds(getPeriod())
				.build();
	}

	protected abstract String getProbePath();

	protected abstract Integer getPort();

	protected abstract int getTimeout();

	protected abstract int getInitialDelay();

	protected abstract int getPeriod();

	protected KubernetesDeployerProperties getKubernetesDeployerProperties() {
		return kubernetesDeployerProperties;
	}

	protected Map<String, String> getDeploymentProperties() {
		return containerConfiguration.getAppDeploymentRequest().getDeploymentProperties();
	}

	protected Integer getDefaultPort() {
		return containerConfiguration.getExternalPort();
	}

	protected boolean useBoot2ProbePath() {
		String bootMajorVersionProperty = KUBERNETES_DEPLOYER_PREFIX + ".bootMajorVersion";

		if (getDeploymentProperties().containsKey(bootMajorVersionProperty)) {
			Integer bootMajorVersion = Integer.valueOf(getDeploymentProperties().get(bootMajorVersionProperty));

			if (bootMajorVersion == BOOT_2_MAJOR_VERSION) {
				return true;
			}
		}

		return false;
	}

	private List<HTTPHeader> getHttpHeaders() {
		List<HTTPHeader> httpHeaders = new ArrayList<>();

		HTTPHeader authenticationHeader = getAuthorizationHeader();

		if (authenticationHeader != null) {
			httpHeaders.add(authenticationHeader);
		}

		return httpHeaders;
	}

	private HTTPHeader getAuthorizationHeader() {
		HTTPHeader httpHeader = null;

		Secret probeCredentialsSecret = containerConfiguration.getProbeCredentialsSecret();

		if (probeCredentialsSecret != null) {
			Assert.isTrue(probeCredentialsSecret.getData().containsKey(PROBE_CREDENTIALS_SECRET_KEY_NAME),
					"Secret does not contain a key by the name of " + PROBE_CREDENTIALS_SECRET_KEY_NAME);

			httpHeader = new HTTPHeader();
			httpHeader.setName(AUTHORIZATION_HEADER_NAME);

			// only Basic auth is supported currently
			httpHeader.setValue(ProbeAuthenticationType.Basic.name() + " " +
					probeCredentialsSecret.getData().get(PROBE_CREDENTIALS_SECRET_KEY_NAME));
		}

		return httpHeader;
	}
}
