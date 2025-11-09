/* Licensed under EPL-2.0 2024-2025. */
package edu.kit.kastel.sdq.intelligrade.extensions.settings;

import java.awt.*;
import java.util.Date;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.OptionTag;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * This class persists all required data for the PlugIn.
 * Secrets (such as the Artemis password) are handled by the IntelliJ secrets provider
 */
@State(name = "edu.kit.kastel.extensions.ArtemisSettingsState", storages = @Storage("IntelliGradeSettings.xml"))
public class ArtemisSettingsState implements PersistentStateComponent<ArtemisSettingsState.InternalState> {
    private static final Logger LOG = Logger.getInstance(ArtemisSettingsState.class);

    private static final ThemeColor OLD_ANNOTATION_COLOR =
            new ThemeColor(new Color(155, 54, 54), new Color(155, 54, 54));
    private static final ThemeColor DEFAULT_ANNOTATION_COLOR =
            new ThemeColor(new Color(225, 128, 128), new Color(75, 30, 30));
    private final InternalState state = new InternalState();

    // Settings need to be public for IntelliJ to serialize them
    @SuppressWarnings("java:S1104")
    public static class InternalState {
        public boolean useTokenLogin = true;
        public VCSAccessOption vcsAccessOption = VCSAccessOption.TOKEN;
        public String username = "";
        public String artemisInstanceUrl = "";
        public AutograderOption autograderOption = AutograderOption.FROM_GITHUB;
        public String autograderPath = null;
        public boolean autoOpenMainClass = true;
        public String selectedGradingConfigPath;

        public Date jwtExpiry = new Date(Long.MAX_VALUE);

        @OptionTag(converter = ThemeColor.ThemeColorConverter.class)
        public ThemeColor annotationColor = DEFAULT_ANNOTATION_COLOR;

        @OptionTag(converter = ThemeColor.ThemeColorConverter.class)
        public ThemeColor activeAssessmentButtonColor = ThemeColor.yellow();

        @OptionTag(converter = ThemeColor.ThemeColorConverter.class)
        public ThemeColor finishedAssessmentButtonColor = ThemeColor.magenta();

        @OptionTag(converter = ThemeColor.ThemeColorConverter.class)
        public ThemeColor reportingAssessmentButtonColor = ThemeColor.green();
    }

    public static ArtemisSettingsState getInstance() {
        return ApplicationManager.getApplication().getService(ArtemisSettingsState.class);
    }

    /**
     * Gets the Settings state.
     *
     * @return state.a component state.
     * All properties, public and annotated fields are serialized.
     * Only values which differ from the default (i.e. the value of newly instantiated class)
     * are serialized. {@code null} value indicates that the returned state won't be stored,
     * as a result previously stored state will be used.
     * @see XmlSerializer
     */
    @Override
    public @Nullable InternalState getState() {
        return state;
    }

    /**
     * This method is called when a new component state is loaded.
     * The method can and will be called several times if config
     * files are externally changed while the IDE is running.
     * <p>State object should be used directly, defensive copying is not required.</p>
     *
     * @param state loaded component state
     * @see XmlSerializerUtil#copyBean(Object, Object)
     */
    @Override
    public void loadState(@NonNull InternalState state) {
        XmlSerializerUtil.copyBean(state, this.state);

        // The annotation color type was changed from an int to a ThemeColor.
        // The ThemeColorConverter is able to handle converting a single number to a ThemeColor,
        // but it would not apply the new default colors.
        //
        // There seems to be a dedicated mechanism for migrating configs, but there is not much
        // documentation on it. According to what is there, it should only be used when the
        // config changes in a major way.
        //
        // That is why the config is migrated here.
        if (this.state.annotationColor.equals(OLD_ANNOTATION_COLOR)) {
            LOG.debug("Migrating old annotation color to new format");
            this.state.annotationColor = DEFAULT_ANNOTATION_COLOR;
        }
    }

    public boolean isUseTokenLogin() {
        return state.useTokenLogin;
    }

    public void setUseTokenLogin(boolean useTokenLogin) {
        state.useTokenLogin = useTokenLogin;
    }

    public String getUsername() {
        return state.username;
    }

    public void setUsername(String username) {
        state.username = username;
    }

    public String getArtemisInstanceUrl() {
        return state.artemisInstanceUrl;
    }

    public void setArtemisInstanceUrl(String artemisInstanceUrl) {
        // invalidate JWT if URL changed
        ArtemisCredentialsProvider.getInstance().setJwt("");
        state.artemisInstanceUrl = artemisInstanceUrl;
    }

    public @Nullable String getSelectedGradingConfigPath() {
        return state.selectedGradingConfigPath;
    }

    public void setSelectedGradingConfigPath(@Nullable String selectedGradingConfigPath) {
        state.selectedGradingConfigPath = selectedGradingConfigPath;
    }

    public ThemeColor getAnnotationColor() {
        return state.annotationColor;
    }

    public void setAnnotationColor(ThemeColor annotationColor) {
        state.annotationColor = annotationColor;
    }

    public Date getJwtExpiry() {
        return state.jwtExpiry;
    }

    public void setJwtExpiry(Date jwtExpiry) {
        state.jwtExpiry = jwtExpiry;
    }

    public AutograderOption getAutograderOption() {
        return state.autograderOption;
    }

    public void setAutograderOption(AutograderOption autograderOption) {
        state.autograderOption = autograderOption;
    }

    public String getAutograderPath() {
        return state.autograderPath;
    }

    public void setAutograderPath(String autograderPath) {
        state.autograderPath = autograderPath;
    }

    public boolean isAutoOpenMainClass() {
        return state.autoOpenMainClass;
    }

    public void setAutoOpenMainClass(boolean autoOpenMainClass) {
        state.autoOpenMainClass = autoOpenMainClass;
    }

    public VCSAccessOption getVcsAccessOption() {
        return state.vcsAccessOption;
    }

    public void setVcsAccessOption(VCSAccessOption vcsAccessOption) {
        state.vcsAccessOption = vcsAccessOption;
    }

    public ThemeColor getActiveAssessmentButtonColor() {
        return state.activeAssessmentButtonColor;
    }

    public void setActiveAssessmentButtonColor(ThemeColor activeAssessmentButtonColor) {
        state.activeAssessmentButtonColor = activeAssessmentButtonColor;
    }

    public ThemeColor getFinishedAssessmentButtonColor() {
        return state.finishedAssessmentButtonColor;
    }

    public void setFinishedAssessmentButtonColor(ThemeColor finishedAssessmentButtonColor) {
        state.finishedAssessmentButtonColor = finishedAssessmentButtonColor;
    }

    public ThemeColor getReportingAssessmentButtonColor() {
        return state.reportingAssessmentButtonColor;
    }

    public void setReportingAssessmentButtonColor(ThemeColor reportingAssessmentButtonColor) {
        state.reportingAssessmentButtonColor = reportingAssessmentButtonColor;
    }
}
