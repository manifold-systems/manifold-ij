package ext.jailbreak;

import manifold.ext.api.Jailbreak;

public class ExerciseJailbreakWithError extends ext.jailbreak.stuff.Base
{
  public void testJailbreakErrors()
  {
    ext.jailbreak.stuff.@Jailbreak SecretParam secretParam =
      new ext.jailbreak.stuff.@Jailbreak SecretParam();
    secretParam._foo++;
    secretParam._foo += 9;
  }
}