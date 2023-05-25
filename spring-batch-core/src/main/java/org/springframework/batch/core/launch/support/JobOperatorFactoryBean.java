/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.batch.core.launch.support;

import java.util.Properties;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.converter.DefaultJobParametersConverter;
import org.springframework.batch.core.converter.JobParametersConverter;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.util.Assert;

/**
 * Convenient factory bean that creates a transactional proxy around a
 * {@link JobOperator}.
 *
 * @see JobOperator
 * @see SimpleJobOperator
 * @author Mahmoud Ben Hassine
 * @since 5.0
 */
public class JobOperatorFactoryBean implements FactoryBean<JobOperator>, InitializingBean {

	private static final String TRANSACTION_ISOLATION_LEVEL_PREFIX = "ISOLATION_";

	private static final String TRANSACTION_PROPAGATION_PREFIX = "PROPAGATION_";

	private PlatformTransactionManager transactionManager;

	private TransactionAttributeSource transactionAttributeSource;

	private JobRegistry jobRegistry;

	private JobLauncher jobLauncher;

	private JobRepository jobRepository;

	private JobExplorer jobExplorer;

	private JobParametersConverter jobParametersConverter = new DefaultJobParametersConverter();

	private final ProxyFactory proxyFactory = new ProxyFactory();

	@Override
	public void afterPropertiesSet() throws Exception {
		Assert.notNull(this.transactionManager, "TransactionManager must not be null");
		Assert.notNull(this.jobLauncher, "JobLauncher must not be null");
		Assert.notNull(this.jobRegistry, "JobLocator must not be null");
		Assert.notNull(this.jobExplorer, "JobExplorer must not be null");
		Assert.notNull(this.jobRepository, "JobRepository must not be null");
		if (this.transactionAttributeSource == null) {
			Properties transactionAttributes = new Properties();
			String transactionProperties = String.join(",", TRANSACTION_PROPAGATION_PREFIX + Propagation.REQUIRED,
					TRANSACTION_ISOLATION_LEVEL_PREFIX + Isolation.DEFAULT);
			transactionAttributes.setProperty("stop*", transactionProperties);
			this.transactionAttributeSource = new NameMatchTransactionAttributeSource();
			((NameMatchTransactionAttributeSource) transactionAttributeSource).setProperties(transactionAttributes);
		}
	}

	/**
	 * Setter for the job registry.
	 * @param jobRegistry the job registry to set
	 */
	public void setJobRegistry(JobRegistry jobRegistry) {
		this.jobRegistry = jobRegistry;
	}

	/**
	 * Setter for the job launcher.
	 * @param jobLauncher the job launcher to set
	 */
	public void setJobLauncher(JobLauncher jobLauncher) {
		this.jobLauncher = jobLauncher;
	}

	/**
	 * Setter for the job repository.
	 * @param jobRepository the job repository to set
	 */
	public void setJobRepository(JobRepository jobRepository) {
		this.jobRepository = jobRepository;
	}

	/**
	 * Setter for the job explorer.
	 * @param jobExplorer the job explorer to set
	 */
	public void setJobExplorer(JobExplorer jobExplorer) {
		this.jobExplorer = jobExplorer;
	}

	/**
	 * Setter for the job parameters converter.
	 * @param jobParametersConverter the job parameters converter to set
	 */
	public void setJobParametersConverter(JobParametersConverter jobParametersConverter) {
		this.jobParametersConverter = jobParametersConverter;
	}

	/**
	 * Setter for the transaction manager.
	 * @param transactionManager the transaction manager to set
	 */
	public void setTransactionManager(PlatformTransactionManager transactionManager) {
		this.transactionManager = transactionManager;
	}

	/**
	 * Set the transaction attributes source to use in the created proxy.
	 * @param transactionAttributeSource the transaction attributes source to use in the
	 * created proxy.
	 */
	public void setTransactionAttributeSource(TransactionAttributeSource transactionAttributeSource) {
		Assert.notNull(transactionAttributeSource, "transactionAttributeSource must not be null.");
		this.transactionAttributeSource = transactionAttributeSource;
	}

	@Override
	public Class<?> getObjectType() {
		return JobOperator.class;
	}

	@Override
	public boolean isSingleton() {
		return true;
	}

	@Override
	public JobOperator getObject() throws Exception {
		TransactionInterceptor advice = new TransactionInterceptor((TransactionManager) this.transactionManager,
				this.transactionAttributeSource);
		this.proxyFactory.addAdvice(advice);
		this.proxyFactory.setProxyTargetClass(false);
		this.proxyFactory.addInterface(JobOperator.class);
		this.proxyFactory.setTarget(getTarget());
		return (JobOperator) this.proxyFactory.getProxy(getClass().getClassLoader());
	}

	private SimpleJobOperator getTarget() throws Exception {
		SimpleJobOperator simpleJobOperator = new SimpleJobOperator();
		simpleJobOperator.setJobRegistry(this.jobRegistry);
		simpleJobOperator.setJobExplorer(this.jobExplorer);
		simpleJobOperator.setJobRepository(this.jobRepository);
		simpleJobOperator.setJobLauncher(this.jobLauncher);
		simpleJobOperator.setJobParametersConverter(this.jobParametersConverter);
		simpleJobOperator.afterPropertiesSet();
		return simpleJobOperator;
	}

}
