package pl.edu.agh.io.pdptw.algorithm.generation;

import java.util.List;

import pl.edu.agh.io.pdptw.configuration.Configuration;
import pl.edu.agh.io.pdptw.model.Request;
import pl.edu.agh.io.pdptw.model.Solution;
import pl.edu.agh.io.pdptw.model.Vehicle;

public interface GenerationAlgorithm {
    public Solution generateSolution(List<Request> requestPool, List<Vehicle> vehicles,
			Configuration configuration) throws IllegalArgumentException;
}
