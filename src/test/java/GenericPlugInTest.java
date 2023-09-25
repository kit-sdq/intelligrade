import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import edu.kit.kastel.extensions.settings.ArtemisSettingsState;

/**
 * Example test that does not work :(
 */
public class GenericPlugInTest extends BasePlatformTestCase {
  public void testPlugInLoaded() {
    BasePlatformTestCase.assertEquals(2, ArtemisSettingsState.getInstance().getColumnsPerRatingGroup());
  }
}
