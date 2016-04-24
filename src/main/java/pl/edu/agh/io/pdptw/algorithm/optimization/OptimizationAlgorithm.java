package pl.edu.agh.io.pdptw.algorithm.optimization;

import java.util.Set;

import pl.edu.agh.io.pdptw.model.Request;
import pl.edu.agh.io.pdptw.model.Solution;

public interface OptimizationAlgorithm {
	public void optimize(Solution solution, Set<Request> requestPool);
}