package edu.kit.kastel.extensions.settings;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
class ArtemisSettingsStateTest extends BasePlatformTestCase {

  private static ArtemisSettingsState settings;

  @BeforeAll
  public static void getSettingsInstance(){
    settings = ArtemisSettingsState.getInstance();
  }
  @Test
  void testDefaultSettingsUrl() {
    Assertions.assertEquals("https://artemis.praktomat.cs.kit.edu", settings.getArtemisInstanceUrl());
  }
}
