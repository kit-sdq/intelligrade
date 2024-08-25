/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.wrappers;

import com.intellij.DynamicBundle;
import edu.kit.kastel.sdq.artemis4j.api.grading.IMistakeType;

public class DisplayableMistakeType extends Displayable<IMistakeType> {

    private static final String LOCALE = DynamicBundle.getLocale().getLanguage();

    public DisplayableMistakeType(IMistakeType item) {
        super(item);
    }

    @Override
    public String toString() {
        return super.getWrappedValue().getButtonText(LOCALE);
    }
}
