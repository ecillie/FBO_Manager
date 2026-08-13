package com.ecillie.fbomanager.platform.api;

public record ApiSort(String field, Direction direction) {

	public enum Direction {
		ASC, DESC
	}

	@Override
	public String toString() {
		return this.field + "," + this.direction.name().toLowerCase();
	}
}
