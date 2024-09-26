/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.sdq.intelligrade.extensions.settings;

import java.awt.*;
import java.util.Date;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.ui.JBColor;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class persists all required data for the PlugIn.
 * Secrets (such as the Artemis password) are handled by the IntelliJ secrets provider
 */
@State(name = "edu.kit.kastel.extensions.ArtemisSettingsState", storages = @Storage("IntelliGradeSettings.xml"))
public class ArtemisSettingsState implements PersistentStateComponent<ArtemisSettingsState.InternalState> {
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
        public int columnsPerRatingGroup = 3;

        public Date jwtExpiry = new Date(Long.MAX_VALUE);

        public int annotationColor = new JBColor(new Color(155, 54, 54), new Color(155, 54, 54)).getRGB();
        public int activeAssessmentButtonColor = JBColor.YELLOW.getRGB();
        public int finishedAssessmentButtonColor = JBColor.MAGENTA.getRGB();
        public int reportingAssessmentButtonColor = JBColor.GREEN.getRGB();
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
    public void loadState(@NotNull InternalState state) {
        XmlSerializerUtil.copyBean(state, this.state);
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

    public int getColumnsPerRatingGroup() {
        return state.columnsPerRatingGroup;
    }

    public void setColumnsPerRatingGroup(int columnsPerRatingGroup) {
        state.columnsPerRatingGroup = columnsPerRatingGroup;
    }

    public Color getAnnotationColor() {
        return new Color(state.annotationColor);
    }

    public void setAnnotationColor(Color annotationColor) {
        state.annotationColor = annotationColor.getRGB();
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

    public Color getActiveAssessmentButtonColor() {
        return new Color(state.activeAssessmentButtonColor);
    }

    public void setActiveAssessmentButtonColor(Color activeAssessmentButtonColor) {
        state.activeAssessmentButtonColor = activeAssessmentButtonColor.getRGB();
    }

    public Color getFinishedAssessmentButtonColor() {
        return new Color(state.finishedAssessmentButtonColor);
    }

    public void setFinishedAssessmentButtonColor(Color finishedAssessmentButtonColor) {
        state.finishedAssessmentButtonColor = finishedAssessmentButtonColor.getRGB();
    }

    public Color getReportingAssessmentButtonColor() {
        return new Color(state.reportingAssessmentButtonColor);
    }

    public void setReportingAssessmentButtonColor(Color reportingAssessmentButtonColor) {
        state.reportingAssessmentButtonColor = reportingAssessmentButtonColor.getRGB();
    }
}
