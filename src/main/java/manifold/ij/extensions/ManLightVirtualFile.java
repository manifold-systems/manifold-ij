package manifold.ij.extensions;

import com.intellij.lang.Language;
import com.intellij.testFramework.LightVirtualFile;
import manifold.ij.core.ManModule;

public class ManLightVirtualFile extends LightVirtualFile
{
  private final ManModule _module;

  public ManLightVirtualFile( ManModule module, String name, Language language, CharSequence text )
  {
    super( name, language, text );
    _module = module;
  }

  public ManModule getModule()
  {
    return _module;
  }
}
