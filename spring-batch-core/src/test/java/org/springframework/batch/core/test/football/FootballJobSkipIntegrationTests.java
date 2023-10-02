/*
 * Copyright 2006-2023 the original author or authors.
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

package org.springframework.batch.core.test.football;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * @author Dave Syer
 * @author Mahmoud Ben Hassine
 *
 */
@SpringJUnitConfig(locations = { "/simple-job-launcher-context.xml", "/META-INF/batch/footballSkipJob.xml" })
public class FootballJobSkipIntegrationTests {

	/** Logger */
	private final Log logger = LogFactory.getLog(getClass());

	@Autowired
	private JobLauncher jobLauncher;

	@Autowired
	private Job job;

	@Test
	void testLaunchJob() throws Exception {
		JobExecution execution = jobLauncher.run(job,
				new JobParametersBuilder().addLong("skip.limit", 0L).toJobParameters());
		assertEquals(BatchStatus.COMPLETED, execution.getStatus());
		for (StepExecution stepExecution : execution.getStepExecutions()) {
			logger.info("Processed: " + stepExecution);
		}
		// They all skip on the second execution because of a primary key
		// violation
		long retryLimit = 2L;
		execution = jobLauncher.run(job,
				new JobParametersBuilder().addLong("skip.limit", 100000L)
					.addLong("retry.limit", retryLimit)
					.toJobParameters());
		assertEquals(BatchStatus.COMPLETED, execution.getStatus());
		for (StepExecution stepExecution : execution.getStepExecutions()) {
			logger.info("Processed: " + stepExecution);
			if ("playerload".equals(stepExecution.getStepName())) {
				// The effect of the retries is to increase the number of
				// rollbacks
				long commitInterval = stepExecution.getReadCount() / (stepExecution.getCommitCount() - 1);
				// Account for the extra empty commit if the read count is
				// commensurate with the commit interval
				long effectiveCommitCount = stepExecution.getReadCount() % commitInterval == 0
						? stepExecution.getCommitCount() - 1 : stepExecution.getCommitCount();
				long expectedRollbacks = Math.max(1, retryLimit) * effectiveCommitCount + stepExecution.getReadCount();
				assertEquals(expectedRollbacks, stepExecution.getRollbackCount());
				assertEquals(stepExecution.getReadCount(), stepExecution.getWriteSkipCount());
			}
		}

	}

}
