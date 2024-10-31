package edu.kit.kastel.sdq.intelligrade.utils;

import edu.kit.kastel.sdq.artemis4j.ArtemisNetworkException;
import edu.kit.kastel.sdq.artemis4j.grading.ArtemisConnection;
import edu.kit.kastel.sdq.intelligrade.state.PluginState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PermissionUtils {

    private PermissionUtils() {
        throw new IllegalAccessError("Utility class");
    }

    /**
     * Get the groups of the assessor that has the current lock
     * Fails silently if the network request fails
     *
     * @return Optional containing a list of all the assessors groups if there are any or Optional::empty if an error
     * occured or no connection is present yet
     */
    public static List<PermissionLevel> getAssessorPermissionLevel(ArtemisConnection connection) {
        Optional<List<String>> groupsFound = Optional.empty();

        try {
            //if the client is not yet connected, querying an assessor is bogus
            groupsFound = Optional.of(connection.getAssessor().getGroups());

        } catch (ArtemisNetworkException ignored) {
            //optional is already empty so we don't do anything
        }

        //check all the groups this assessor belongs to
        List<PermissionLevel> permissionLevels = new ArrayList<>();
        groupsFound.ifPresent(groups -> {
            for (String groupname : groups) {
                //if the string has the correct suffix, we add the permission level
                if (groupname.endsWith("instructors")) {
                    permissionLevels.add(PermissionLevel.INSTRUCTOR);
                }
                if (groupname.endsWith("students")) {
                    permissionLevels.add(PermissionLevel.STUDENT);
                }
                if (groupname.endsWith("tutors")) {
                    permissionLevels.add(PermissionLevel.TUTOR);
                }
            }
        });

        //if no permissions were found, we have none
        if (permissionLevels.isEmpty()) {
            permissionLevels.add(PermissionLevel.NONE);
        }

        return permissionLevels;
    }

    /**
     * Map the string based permission level from the groups
     * to some cleanly switchable enum
     */
    public enum PermissionLevel implements Comparable<PermissionLevel> {
        INSTRUCTOR,
        TUTOR,
        STUDENT,
        NONE
    }
}
