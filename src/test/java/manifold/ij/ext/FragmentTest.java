package manifold.ij.ext;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import java.util.List;
import manifold.ij.AbstractManifoldCodeInsightTest;

public class FragmentTest extends AbstractManifoldCodeInsightTest
{
  public void testPositiveUsage()
  {
    myFixture.configureByFile( "ext/fragment/ExerciseFragments.java" );
//## todo: this test is not compatible with light framework, convert to a "heavy" test somehow
//    EdtTestUtil.runInEdtAndWait( () -> PsiDocumentManager.getInstance( getProject() ).commitAllDocuments() );
//    EdtTestUtil.runInEdtAndWait( () -> PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue() );
//    List<HighlightInfo> highlightInfos = myFixture.doHighlighting( HighlightSeverity.ERROR );
//    assertEmpty( highlightInfos );
  }
}
