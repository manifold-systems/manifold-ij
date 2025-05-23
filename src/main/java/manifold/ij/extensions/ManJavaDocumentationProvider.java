/*
 *
 *  * Copyright (c) 2022 - Manifold Systems LLC
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

package manifold.ij.extensions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionMemory;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.conflictResolvers.JavaMethodsConflictResolver;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import manifold.ext.params.rt.params;
import manifold.ext.rt.api.Jailbreak;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// override standard JavaDocumentProvider to filter out generated params methods/constructors
public class ManJavaDocumentationProvider extends JavaDocumentationProvider
{
  @Override
  public @Nls String generateDoc( PsiElement element, PsiElement originalElement) {
    // for new Class(<caret>) or methodCall(<caret>) proceed from method call or new expression
    // same for new Cl<caret>ass() or method<caret>Call()
    if (element instanceof PsiExpressionList ||
      element instanceof PsiReferenceExpression && element.getParent() instanceof PsiMethodCallExpression ) {
      element = element.getParent();
      originalElement = null;
    }
    if (element instanceof PsiCallExpression && CodeInsightSettings.getInstance().SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION) {
      PsiMethod method = CompletionMemory.getChosenMethod((PsiCallExpression)element);
      if (method != null) element = method;
    }
    if (element instanceof PsiMethodCallExpression) {
      return getMethodCandidateInfo((PsiMethodCallExpression)element);
    }

    if (element instanceof FakePsiElement ) {
      PsiDocCommentBase docCommentBase = PsiTreeUtil.getParentOfType(originalElement, PsiDocCommentBase.class);
      if (docCommentBase != null) {
        element = docCommentBase.getOwner();
      }
    }

    // Try hard for documentation of incomplete new Class instantiation
    PsiElement elt = element;
    if (originalElement != null && !(originalElement instanceof PsiPackage || originalElement instanceof PsiFileSystemItem)) {
      elt = PsiTreeUtil.prevLeaf(originalElement);
    }
    if (elt instanceof PsiErrorElement) {
      elt = elt.getPrevSibling();
    }
    else if (elt != null && !(elt instanceof PsiNewExpression)) {
      elt = elt.getParent();
    }
    if (elt instanceof PsiNewExpression) {
      PsiClass targetClass = null;

      if (element instanceof PsiJavaCodeReferenceElement) {     // new Class<caret>
        PsiElement resolve = ((PsiJavaCodeReferenceElement)element).resolve();
        if (resolve instanceof PsiClass) targetClass = (PsiClass)resolve;
      }
      else if (element instanceof PsiClass) { //Class in completion
        targetClass = (PsiClass)element;
      }
      else if (element instanceof PsiNewExpression) { // new Class(<caret>)
        PsiJavaCodeReferenceElement reference = ((PsiNewExpression)element).getClassReference();
        if (reference != null) {
          PsiElement resolve = reference.resolve();
          if (resolve instanceof PsiClass) targetClass = (PsiClass)resolve;
        }
      }

      if (targetClass != null) {
        PsiMethod[] constructors = targetClass.getConstructors();
        if (constructors.length > 0) {
          if (constructors.length == 1) return generateDoc(constructors[0], originalElement);
          final StringBuilder sb = new StringBuilder();

          for (PsiMethod constructor : constructors) {
            if( constructor.getAnnotation( params.class.getTypeName() ) != null )
            {
              // filter out generated params methods/constructors (manifold-params)
              continue;
            }
            final String str = PsiFormatUtil.formatMethod(constructor, PsiSubstitutor.EMPTY,
              PsiFormatUtilBase.SHOW_NAME |
                PsiFormatUtilBase.SHOW_TYPE |
                PsiFormatUtilBase.SHOW_PARAMETERS,
              PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_NAME);
            @Jailbreak JavaDocumentationProvider _super = this;
            _super.createElementLink(sb, constructor, StringUtil.escapeXmlEntities(str));
          }

          return JavaBundle.message("javadoc.constructor.candidates", targetClass.getName(), sb);
        }
      }
    }

    //external documentation finder
    return generateExternalJavadoc(element);
  }

  private @Nls String getMethodCandidateInfo(PsiMethodCallExpression expr) {
    final PsiResolveHelper rh = JavaPsiFacade.getInstance(expr.getProject()).getResolveHelper();
    final CandidateInfo[] candidates = rh.getReferencedMethodCandidates(expr, true);

    final String text = expr.getText();
    if (candidates.length > 0) {
      if (candidates.length == 1) {
        PsiElement element = candidates[0].getElement();
        if (element instanceof PsiMethod) return generateDoc(element, null);
      }
      final StringBuilder sb = new StringBuilder();
      @NotNull List<? extends CandidateInfo> conflicts = new ArrayList<>( Arrays.asList(candidates));
      JavaMethodsConflictResolver.filterSupers(conflicts, expr.getContainingFile(), null);

      conflicts = conflicts.stream()
        .filter( c -> c.getElement() instanceof PsiMethod m && m.getAnnotation( params.class.getTypeName() ) == null )
        .toList();
      if( conflicts.size() == 1 )
      {
        PsiElement element = conflicts.get( 0 ).getElement();
        return generateDoc( element, null );
      }

      for (final CandidateInfo candidate : conflicts) {
        final PsiElement element = candidate.getElement();

        final String str = PsiFormatUtil.formatMethod((PsiMethod)element, candidate.getSubstitutor(),
          PsiFormatUtilBase.SHOW_NAME |
            PsiFormatUtilBase.SHOW_TYPE |
            PsiFormatUtilBase.SHOW_PARAMETERS,
          PsiFormatUtilBase.SHOW_TYPE);
        @Jailbreak JavaDocumentationProvider _super = this;
        _super.createElementLink(sb, element, StringUtil.escapeXmlEntities(str));
      }

      return CodeInsightBundle.message("javadoc.candidates", text, sb);
    }

    return JavaBundle.message("javadoc.candidates.not.found", text);
  }

}
