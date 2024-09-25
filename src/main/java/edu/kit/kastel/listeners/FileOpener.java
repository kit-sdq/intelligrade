/* Licensed under EPL-2.0 2024. */
package edu.kit.kastel.listeners;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopes;
import com.intellij.psi.search.PsiShortNamesCache;
import edu.kit.kastel.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.state.ActiveAssessment;
import edu.kit.kastel.state.PluginState;
import edu.kit.kastel.utils.EditorUtil;

@Service
public final class FileOpener implements DumbService.DumbModeListener {
    private static final Logger LOG = Logger.getInstance(FileOpener.class);

    private volatile boolean openClassesNextTime = false;

    public static FileOpener getInstance() {
        return ApplicationManager.getApplication().getService(FileOpener.class);
    }

    public FileOpener() {
        PluginState.getInstance().registerAssessmentStartedListener(a -> {
            var settings = ArtemisSettingsState.getInstance();
            synchronized (this) {
                openClassesNextTime = settings.isAutoOpenMainClass();
            }
        });

        PluginState.getInstance().registerAssessmentClosedListener(() -> {
            // Relevant if building indices is not finished before the assessment is closed
            synchronized (this) {
                openClassesNextTime = false;
            }
        });
    }

    @Override
    public void exitDumbMode() {
        if (!openClassesNextTime || !PluginState.getInstance().isAssessing()) {
            return;
        }

        // Open the main class
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            synchronized (this) {
                if (!openClassesNextTime || !PluginState.getInstance().isAssessing()) {
                    return;
                }

                openClassesNextTime = false;
            }

            ApplicationManager.getApplication().runReadAction(() -> {
                var project = EditorUtil.getActiveProject();

                // Only look in assignment/, we aren't interested in test classes
                var directory = VfsUtil.findFile(EditorUtil.getProjectRootDirectory().resolve(ActiveAssessment.ASSIGNMENT_SUB_PATH), true);

                if (directory == null) {
                    LOG.warn("Can't resolve assignment directory");
                    return;
                }

                var scope = GlobalSearchScopes.directoryScope(project, directory, true);
                var mainMethods = PsiShortNamesCache.getInstance(project).getMethodsByName("main", scope);

                PsiType stringType =
                        PsiType.getJavaLangString(PsiManager.getInstance(project), GlobalSearchScope.allScope(project));
                for (var method : mainMethods) {
                    // Is public & static
                    var modifiers = method.getModifierList();
                    if (!modifiers.hasExplicitModifier(PsiModifier.PUBLIC)
                            || !modifiers.hasExplicitModifier(PsiModifier.STATIC)) {
                        continue;
                    }

                    // Returns void
                    if (!PsiTypes.voidType().equals(method.getReturnType())) {
                        continue;
                    }

                    // Single parameter of type String[] or String...
                    var parameters = method.getParameterList();
                    if (parameters.getParametersCount() != 1) {
                        continue;
                    }

                    var parameter = parameters.getParameters()[0];
                    var type = parameter.getType();

                    // This should also cover varargs, since PsiEllipsisType is a subtype of PsiArrayType
                    if (type instanceof PsiArrayType arrayType) {
                        if (!stringType.equals(arrayType.getComponentType())) {
                            continue;
                        }
                    } else {
                        continue;
                    }

                    // All checks passed, this is a main method!
                    var file = method.getContainingFile().getVirtualFile();
                    ApplicationManager.getApplication().invokeLater(() -> {
                        // Open the file in an editor, and place the caret at the main method's declaration
                        FileEditorManager.getInstance(project)
                                .openTextEditor(new OpenFileDescriptor(project, file, method.getTextOffset()), true);

                        // Expand the project view and select the file
                        ProjectView.getInstance(EditorUtil.getActiveProject()).select(null, file, true);
                    });

                    return;
                }

                LOG.info("No main class found");
            });
        });
    }
}
