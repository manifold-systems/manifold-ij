package manifold.ij.extensions;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.light.LightClass;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


/**
 */
public class TestFinder extends PsiElementFinder {
  private final Project _project;
  private final ThreadLocal<Set<String>> _shortcircuit = new ThreadLocal<>();

  public TestFinder(Project project) {
    _project = project;
  }

  @Override
  public PsiClass[] findClasses(String fqn, GlobalSearchScope scope) {
    if (isShortCircuit(fqn)) {
      return PsiClass.EMPTY_ARRAY;
    }

    addShortCircuit(fqn);
    try {
      PsiClass[] classes = JavaPsiFacade.getInstance(scope.getProject()).findClasses(fqn, scope);
      return Arrays.stream(classes).map(LightClass::new).toArray(PsiClass[]::new);
    } finally {
      removeShortCircuit(fqn);
    }
  }

  @Override
  public PsiClass findClass(String fqn, GlobalSearchScope scope) {
    if (isShortCircuit(fqn)) {
      return null;
    }

    addShortCircuit(fqn);
    try {
      PsiClass cls = JavaPsiFacade.getInstance(scope.getProject()).findClass(fqn, scope);
      return cls == null ? null : new LightClass(cls);
    } finally {
      removeShortCircuit(fqn);
    }
  }

  @NotNull
  @Override
  public PsiClass[] getClasses(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    String fqn = psiPackage.getQualifiedName();
    if (isShortCircuit(fqn)) {
      return PsiClass.EMPTY_ARRAY;
    }

    addShortCircuit(fqn);
    try {
      PsiClass[] classes = psiPackage.getClasses(scope);
      return Arrays.stream(classes).map(LightClass::new).toArray(PsiClass[]::new);
    } finally {
      removeShortCircuit(fqn);
    }
  }

  private void addShortCircuit(String fqn) {
    initShortCircuit();
    _shortcircuit.get().add(fqn);
  }

  private void initShortCircuit() {
    if (_shortcircuit.get() == null) {
      _shortcircuit.set(new HashSet<>());
    }
  }

  private void removeShortCircuit(String fqn) {
    initShortCircuit();
    _shortcircuit.get().remove(fqn);
  }

  private boolean isShortCircuit(String fqn) {
    initShortCircuit();
    return _shortcircuit.get().contains(fqn);
  }
}
