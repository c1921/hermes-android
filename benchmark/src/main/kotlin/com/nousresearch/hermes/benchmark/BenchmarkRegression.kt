package com.nousresearch.hermes.benchmark

data class RegressionFailure(
    val metric: String,
    val baseline: Double,
    val candidate: Double,
    val maximumAccepted: Double,
)

object BenchmarkRegression {
    const val MAX_RELATIVE_REGRESSION = 0.10

    fun findFailures(
        baseline: Map<String, Double>,
        candidate: Map<String, Double>,
        maxRelativeRegression: Double = MAX_RELATIVE_REGRESSION,
    ): List<RegressionFailure> {
        require(maxRelativeRegression >= 0.0) { "maxRelativeRegression must be non-negative" }
        val metricNames = baseline.keys + candidate.keys
        return metricNames.distinct().sorted().mapNotNull { metric ->
            val baselineValue = baseline[metric]
            val candidateValue = candidate[metric]
            if (baselineValue == null || candidateValue == null) {
                return@mapNotNull RegressionFailure(
                    metric = metric,
                    baseline = baselineValue ?: Double.NaN,
                    candidate = candidateValue ?: Double.NaN,
                    maximumAccepted = Double.NaN,
                )
            }
            require(baselineValue.isFinite() && baselineValue >= 0.0) {
                "baseline metric '$metric' must be finite and non-negative"
            }
            require(candidateValue.isFinite() && candidateValue >= 0.0) {
                "candidate metric '$metric' must be finite and non-negative"
            }
            val maximumAccepted = baselineValue * (1.0 + maxRelativeRegression)
            if (candidateValue > maximumAccepted) {
                RegressionFailure(metric, baselineValue, candidateValue, maximumAccepted)
            } else {
                null
            }
        }
    }

    fun isAccepted(
        baseline: Map<String, Double>,
        candidate: Map<String, Double>,
        maxRelativeRegression: Double = MAX_RELATIVE_REGRESSION,
    ): Boolean = findFailures(baseline, candidate, maxRelativeRegression).isEmpty()
}
