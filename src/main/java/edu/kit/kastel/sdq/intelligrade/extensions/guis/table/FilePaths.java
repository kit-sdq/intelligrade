/* Licensed under EPL-2.0 2024-2025. */
package edu.kit.kastel.sdq.intelligrade.extensions.guis.table;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public record FilePaths(List<String> filePaths) implements Comparable<FilePaths> {
    public FilePaths {
        // remove duplicates and sort the file paths
        filePaths = filePaths.stream()
                .map(path -> path.replace("\\", "/"))
                .sorted()
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<PathSegment> getFileTree() {
        List<PathSegment> fileTree = new ArrayList<>();

        for (String filePath : filePaths) {
            List<String> path = List.of(filePath.split("/"));
            PathSegment segment = new PathSegment(path.getLast());

            for (int i = path.size() - 2; i >= 0; i--) {
                segment = new PathSegment(path.get(i), List.of(segment));
            }

            // try to find a segment that is already in the tree and add the segment to it if possible
            boolean hasBeenAdded = false;
            for (PathSegment treeSegment : fileTree) {
                if (treeSegment.addIfPossible(segment)) {
                    hasBeenAdded = true;
                    break;
                }
            }

            if (!hasBeenAdded) {
                fileTree.add(segment);
            }
        }

        return fileTree;
    }

    @Override
    public int compareTo(FilePaths other) {
        var left = filePaths;
        var right = other.filePaths;

        for (int i = 0; i < Math.min(left.size(), right.size()); i++) {
            var comparison = left.get(i).compareTo(right.get(i));
            if (comparison != 0) {
                return comparison;
            }
        }

        return Integer.compare(left.size(), right.size());
    }

    // This is how the file tree would look like for file paths:
    // src/(edu/(Main.java, Other.java), test/Test.java)

    @Override
    public String toString() {
        return getFileTree().stream().map(PathSegment::toString).collect(Collectors.joining(", "));
    }

    private record PathSegment(String name, List<PathSegment> elements) {
        public PathSegment {
            elements = new ArrayList<>(elements);
        }

        public PathSegment(String name) {
            this(name, List.of());
        }

        public boolean addIfPossible(PathSegment segment) {
            if (!this.name().equals(segment.name())) {
                return false;
            }

            // the current name is shared, therefore try to merge the children:
            List<PathSegment> remaining = new ArrayList<>(segment.elements());
            for (var elem : elements) {
                remaining.removeIf(elem::addIfPossible);
            }

            elements.addAll(remaining);
            return true;
        }

        @Override
        public String toString() {
            if (elements.isEmpty()) {
                return name;
            }

            String result = name + "/";

            if (elements.size() == 1) {
                return result + elements.getFirst();
            }

            return result + "(" + elements.stream().map(PathSegment::toString).collect(Collectors.joining(", ")) + ")";
        }
    }
}
