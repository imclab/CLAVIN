package com.berico.clavin.resolver.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.berico.clavin.Options;
import com.berico.clavin.extractor.CoordinateOccurrence;
import com.berico.clavin.extractor.ExtractionContext;
import com.berico.clavin.extractor.LocationOccurrence;
import com.berico.clavin.resolver.LocationResolver;
import com.berico.clavin.resolver.ResolutionContext;
import com.berico.clavin.resolver.ResolvedCoordinate;
import com.berico.clavin.resolver.ResolvedLocation;

/*#####################################################################
 * 
 * CLAVIN (Cartographic Location And Vicinity INdexer)
 * ---------------------------------------------------
 * 
 * Copyright (C) 2012-2013 Berico Technologies
 * http://clavin.bericotechnologies.com
 * 
 * ====================================================================
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * 		http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 * 
 * ====================================================================
 * 
 * DefaultLocationResolver.java
 * 
 *###################################################################*/

/**
 * Non-parallelized workflow for resolving locations and coordinates
 * from an ExtractionContext.  This implementation breaks the resolution
 * process into 5 steps:
 *   1.  Find Location Candidates
 *   2.  Find Coordinate Candidates
 *   3.  Select (filter) Location Candidates
 *   4.  Select (filter) Coordinate Candidates
 *   5.  Reduce (last pass filter) Location and Coordinate Candidates.
 *  
 * This process is intentionally parallelizable:  1 -> 3 -> 5 <- 4 <- 2
 */
public class DefaultLocationResolver implements LocationResolver {

	private static final Logger logger = LoggerFactory.getLogger(DefaultLocationResolver.class);
	
	LocationNameIndex locationNameIndex;
	CoordinateIndex coordinateIndex;
	LocationCandidateSelectionStrategy locationSelectionStrategy;
	CoordinateCandidateSelectionStrategy coordinateSelectionStrategy;
	ResolutionResultsReductionStrategy reductionStrategy;
	Options defaultOps = new Options();
	
	/**
	 * Provide the 5 workflow steps necessary to perform resolution.
	 * @param locationNameIndex Index of location names
	 * @param coordinateIndex Index of coordinates
	 * @param locationSelectionStrategy Selection strategy for filtering locations
	 * @param coordinateSelectionStrategy Selection strategy for filtering coordinates
	 * @param reductionStrategy Strategy for reducing the final results
	 */
	public DefaultLocationResolver(
			LocationNameIndex locationNameIndex,
			CoordinateIndex coordinateIndex,
			LocationCandidateSelectionStrategy locationSelectionStrategy,
			CoordinateCandidateSelectionStrategy coordinateSelectionStrategy,
			ResolutionResultsReductionStrategy reductionStrategy) {
		
		this.locationNameIndex = locationNameIndex;
		this.coordinateIndex = coordinateIndex;
		this.locationSelectionStrategy = locationSelectionStrategy;
		this.coordinateSelectionStrategy = coordinateSelectionStrategy;
		this.reductionStrategy = reductionStrategy;
	}

	/**
	 * Provided an ExtractionContext (Locations and Coordinates), return 
	 * a list of Resolved Locations and Coordinates (ResolutionContext).
	 * @param context Extraction Context
	 * @return ResolutionContext (Resolved Locations and Coordinates)
	 */
	@Override
	public ResolutionContext resolveLocations(ExtractionContext context) throws Exception {
		
		return resolveLocations(context, defaultOps);
	}

	/**
	 * Provided an ExtractionContext (Locations and Coordinates), return 
	 * a list of Resolved Locations and Coordinates (ResolutionContext).
	 * @param context Extraction Context
	 * @param options Options used to coach the resolver.
	 * @return ResolutionContext (Resolved Locations and Coordinates)
	 */
	@Override
	public ResolutionContext resolveLocations(
			ExtractionContext context, Options options)
			throws Exception {
		
		logger.debug("Beginning resolution step.");
		
		ArrayList<List<ResolvedLocation>> locationCandidates = 
				findLocationCandidates(context.getLocations(), options);
		
		logger.debug("Found {} location candidate lists.", locationCandidates.size());
		
		ArrayList<List<ResolvedCoordinate>> coordinateCandidates =
				findCoordinateCandidates(context.getCoordinates(), options);
		
		logger.debug("Found {} coordinate candidate lists.", coordinateCandidates.size());
		
		List<ResolvedCoordinate> resolvedCoordinates = 
			coordinateSelectionStrategy.select(
					coordinateCandidates, context.getLocations(), options);
		
		logger.debug("Selected {} coordinates.", resolvedCoordinates.size());
		
		List<ResolvedLocation> resolvedLocations = 
			locationSelectionStrategy.select(
					locationCandidates, context.getCoordinates(), options);
		
		logger.debug("Selected {} locations.", resolvedLocations.size());
		
		return reductionStrategy.reduce(context, resolvedLocations, resolvedCoordinates);
	}
	
	
	/**
	 * Find potential location candidates from the extracted occurrences.
	 * 
	 * @param locations Location Occurrences found in text.
	 * @param options Options to help configure the index.
	 * @return List of candidates for each Location Occurrence.
	 * @throws Exception
	 */
	protected ArrayList<List<ResolvedLocation>> findLocationCandidates(
			Collection<LocationOccurrence> locations, Options options) 
			throws Exception {
		
		ArrayList<List<ResolvedLocation>> candidates = 
				new ArrayList<List<ResolvedLocation>>();
		
		for (LocationOccurrence occurrence : locations){
			
			List<ResolvedLocation> searchResults = locationNameIndex.search(occurrence, options);
			
			// We absolutely do not want empty lists since they will
			// screw up the optimization step!
			if (searchResults.size() > 0) candidates.add(searchResults);
		}
		
		return candidates;
	}

	/**
	 * Find potential coordinate candidates from the extracted coordinates.
	 * 
	 * @param coordinates Coordinate Occurrences found in text.
	 * @param options Options to help configure the index.
	 * @return List of candidates for each Coordinate Occurrence.
	 * @throws Exception
	 */
	protected ArrayList<List<ResolvedCoordinate>> findCoordinateCandidates(
			Collection<CoordinateOccurrence<?>> coordinates, Options options) 
			throws Exception {
		
		ArrayList<List<ResolvedCoordinate>> candidates =
				new ArrayList<List<ResolvedCoordinate>>();
		
		for (CoordinateOccurrence<?> coordinate : coordinates){
			
			List<ResolvedCoordinate> searchResults = coordinateIndex.search(coordinate, options);
			
			// We absolutely do not want empty lists since they will
			// screw up the optimization step!
			if (searchResults.size() > 0) candidates.add(searchResults);
		}
		
		return candidates;
	}
}
