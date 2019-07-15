package it.unibz.inf.ontop.answering.reformulation.rewriting.impl;

import com.google.common.collect.ImmutableSet;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collector;

/**
 * Represents intersections of classes or properties as 
 *     all the sub-classes (resp., sub-properties).
 * 
 * Such a representation makes containment checks fast.
 * 
 * @author Roman Kontchakov
 *
 * @param <T>
 */

public class Intersection<T> {

	/**
	 * downward-saturated set
	 * 		(contains all sub-class or sub-properties) 
	 * 
	 * null represents the maximal element -- top
	 * the empty set is the minimal element -- bottom
	 */
	private final ImmutableSet<T> elements;

	private Intersection(ImmutableSet<T> elements) {
		this.elements = elements;
	}

	/**
	 * checks if the intersection is entailed (subsumes) e
	 *  
	 * @param e a class or a property
	 *  
	 * @return true if e entails (is subsumed) by the intersection 
	 */
	
	public boolean subsumes(T e) {
		// top contains everything
		return (elements == null) || elements.contains(e);
	}
	
	/**
	 * returns the intersection with a class / property
	 * 
	 * IMPORTANT: the class / property is given by the DOWNWARD-SATURATED SET
	 *              (in other words, by the result of EquivalencesDAG.getSubRepresentatives
	 * 
	 * @param e a non-empty downward saturated set for class / property
	 */
	
	public Intersection<T> intersectionWith(Collection<T> e) {

		Collection<T> result;
		if (elements != null) {
			Set<T> set = new HashSet<>(elements);
			set.retainAll(e);
			result = set;
		}
		else
			result = e; // we have top, the intersection is sub

		return result.isEmpty()
				? BOTTOM
				: new Intersection<>(ImmutableSet.copyOf(result)); // copy the set
	}
	
	/**
	 * intersection of two intersections
	 *
	 * @param i1
	 * @param i2
	 */
	
	public static <T> Intersection<T> intersectionOf(Intersection<T> i1, Intersection<T> i2) {
		if (i1.elements == null) // i1 is top
			return i2;

		return i1.elements.isEmpty()
				? i1   // i1 is bottom
				: i2.intersectionWith(i1.elements);
	}


	private static final Intersection TOP = new Intersection<>(null);
	private static final Intersection BOTTOM = new Intersection<>(ImmutableSet.of());

	public static <T> Intersection<T> top() { return TOP; }
	public static <T> Intersection<T> bottom() { return BOTTOM; }

	public boolean isBottom() { return elements != null && elements.isEmpty(); }

	private final static class Accumulator<T> {
		private Intersection<T> r = Intersection.top();

		Accumulator<T> intersectWith(Intersection<T> i) { r = intersectionOf(r, i); return this; }
		Accumulator<T> intersectWith(Collection<T> e) { r = r.intersectionWith(e); return this; }
		Accumulator<T> intersectWith(Accumulator<T> a) { r = intersectionOf(r, a.r); return this; }

		Intersection<T> result() { return r; }
	}


	public static <T> Collector<Intersection<T>, Accumulator<T>, Intersection<T>> toIntersection() {
		return Collector.of(
				Accumulator::new, // Supplier
				Accumulator::intersectWith, // Accumulator
				Accumulator::intersectWith, // Merger
				Accumulator::result, // Finisher
				Collector.Characteristics.UNORDERED);
	}

	public static <T> Collector<ImmutableSet<T>, Accumulator<T>, Intersection<T>> toIntersectionOfSets() {
		return Collector.of(
				Accumulator::new, // Supplier
				Accumulator::intersectWith, // Accumulator
				Accumulator::intersectWith, // Merger
				Accumulator::result, // Finisher
				Collector.Characteristics.UNORDERED);
	}


	@Override
	public String toString() {
		return ((elements == null) ? "TOP" : elements.toString());
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof Intersection) {
			Intersection other = (Intersection)o;
			return this.elements == null && other.elements == null ||
					this.elements != null && this.elements.equals(other.elements);
		}
		return false;
	}
}
