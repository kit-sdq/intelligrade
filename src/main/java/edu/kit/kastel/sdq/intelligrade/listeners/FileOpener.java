/* Licensed under EPL-2.0 2024-2025. */
package edu.kit.kastel.sdq.intelligrade.listeners;

import java.util.Arrays;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopes;
import com.intellij.psi.search.PsiShortNamesCache;
import edu.kit.kastel.sdq.intelligrade.extensions.settings.ArtemisSettingsState;
import edu.kit.kastel.sdq.intelligrade.state.ActiveAssessment;
import edu.kit.kastel.sdq.intelligrade.state.PluginState;
import edu.kit.kastel.sdq.intelligrade.utils.IntellijUtil;

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
        // Do this in the background because it may cause a synchronous vfs refresh
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            synchronized (this) {
                if (!openClassesNextTime || !PluginState.getInstance().isAssessing()) {
                    return;
                }

                openClassesNextTime = false;
            }

            var project = IntellijUtil.getActiveProject();

            // Only look in assignment/, we aren't interested in test classes
            var directory = VfsUtil.findFile(
                    IntellijUtil.getProjectRootDirectory().resolve(ActiveAssessment.ASSIGNMENT_SUB_PATH), true);

            if (directory == null) {
                LOG.warn("Can't resolve assignment directory");
                return;
            }

            // Even though we exited dumb mode, the index operations below may throw IndexNotReadyExceptions,
            // so defensively wrap this in a smart mode action
            DumbService.getInstance(project).runReadActionInSmartMode(() -> findAnOpenMainMethod(project, directory));
        });
    }

    private static void findAnOpenMainMethod(Project project, VirtualFile directory) {
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
            openFile(project, method.getContainingFile().getVirtualFile(), method.getTextOffset());
            return;
        }

        LOG.info("No main class found");

        // if it could not find a main class, open the first class in the directory:
        for (String className : PsiShortNamesCache.getInstance(project).getAllClassNames()) {
            var psiClass = Arrays.stream(PsiShortNamesCache.getInstance(project).getClassesByName(className, scope))
                    .findFirst();
            if (psiClass.isPresent()) {
                var file = psiClass.get().getContainingFile().getVirtualFile();
                openFile(project, file, 0);
                return;
            }
        }
    }

    private static void openFile(Project project, VirtualFile file, int offset) {
        ApplicationManager.getApplication().invokeLater(() -> {
            // Open the file in an editor, and place the caret at the main method's declaration
            FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, file, offset), true);

            // Expand the project view and select the file
            ProjectView.getInstance(IntellijUtil.getActiveProject()).select(null, file, true);
        });
    }
}
