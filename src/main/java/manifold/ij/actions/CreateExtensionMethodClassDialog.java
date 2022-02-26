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

package manifold.ij.actions;

import com.intellij.ide.actions.ElementCreator;
import com.intellij.ide.util.TreeJavaClassChooserDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import manifold.ij.util.ManBundle;

public class CreateExtensionMethodClassDialog extends TreeJavaClassChooserDialog
{
  private JTextField _fieldName;

  private ElementCreator myCreator;

  public CreateExtensionMethodClassDialog( Project project )
  {
    super( "Create Extension Method Class", project, GlobalSearchScope.allScope( project ), null, null );
  }

  public String getEnteredName()
  {
    return _fieldName.getText();
  }

  public String getExtendedClassName()
  {
    PsiClass psiClass = super.calcSelectedClass();
    return psiClass == null ? null : psiClass.getQualifiedName();
  }

  @Override
  protected JComponent createCenterPanel()
  {
    final JPanel panel = new JPanel( new GridBagLayout() );
    panel.setBorder( BorderFactory.createEmptyBorder( 8, 8, 8, 8 ) );

    final GridBagConstraints c = new GridBagConstraints();

    int iY = 0;

    c.anchor = GridBagConstraints.WEST;
    c.fill = GridBagConstraints.NONE;
    c.gridx = 0;
    c.gridy = iY++;
    c.weightx = 1;
    c.weighty = 0;
    c.insets = new Insets( 2, 2, 0, 0 );
    panel.add( new JLabel( ManBundle.message( "type.Class.Name" ) ), c );

    c.anchor = GridBagConstraints.WEST;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.gridx = 0;
    c.gridy = iY++;
    c.gridwidth = GridBagConstraints.REMAINDER;
    c.gridheight = 1;
    c.weightx = 1;
    c.weighty = 0;
    c.insets = new Insets( 2, 2, 5, 0 );
    panel.add( _fieldName = new IdentifierTextField(), c );

    c.anchor = GridBagConstraints.WEST;
    c.fill = GridBagConstraints.NONE;
    c.gridx = 0;
    c.gridy = iY++;
    c.gridwidth = 1;
    c.gridheight = 1;
    c.weightx = 1;
    c.weighty = 0;
    c.insets = new Insets( 2, 2, 2, 0 );
    panel.add( new JLabel( ManBundle.message( "action.create.extended.type" ) ), c );

    c.anchor = GridBagConstraints.WEST;
    c.fill = GridBagConstraints.BOTH;
    c.gridx = 0;
    c.gridy = iY;
    c.gridwidth = GridBagConstraints.REMAINDER;
    c.gridheight = 1;
    c.weightx = 1;
    c.weighty = 1;
    c.insets = new Insets( 2, 2, 0, 0 );
    JComponent centerPanel = super.createCenterPanel();
    panel.add( centerPanel, c );

    //noinspection deprecation
    _fieldName.setNextFocusableComponent( getGotoByNamePanel().getPreferredFocusedComponent() );

    return panel;
  }

  @Override
  protected void doOKAction()
  {
    if( myCreator.tryCreate( getEnteredName() ).length == 0 )
    {
      if( getTabbedPane().getSelectedIndex() == 0 )
      {
        getGotoByNamePanel().rebuildList( false );
      }
      return;
    }
    super.doOKAction();
  }

  @Override
  public JComponent getPreferredFocusedComponent()
  {
    return _fieldName;
  }

  public static Builder createDialog( Project project )
  {
    final CreateExtensionMethodClassDialog dialog = new CreateExtensionMethodClassDialog( project );
    return new BuilderImpl( dialog, project );
  }

  public interface Builder
  {
    Builder setTitle( String title );

    <T extends PsiElement> T show( String errorTitle, FileCreator<T> creator );
  }

  private static class BuilderImpl implements Builder
  {
    private final CreateExtensionMethodClassDialog myDialog;
    private final Project myProject;

    public BuilderImpl( CreateExtensionMethodClassDialog dialog, Project project )
    {
      myDialog = dialog;
      myProject = project;
    }

    @Override
    public Builder setTitle( String title )
    {
      myDialog.setTitle( title );
      return this;
    }

    public <T extends PsiElement> T show( String errorTitle,
                                          final FileCreator<T> creator )
    {
      final Ref<T> created = Ref.create( null );
      myDialog.myCreator = new ElementCreator( myProject, errorTitle )
      {
        @Override
        protected PsiElement[] create( String newName ) throws Exception
        {
          final T element = creator.createFile( newName, myDialog.getExtendedClassName() );
          created.set( element );
          if( element != null )
          {
            return new PsiElement[]{element};
          }
          return PsiElement.EMPTY_ARRAY;
        }

        @Override
        protected String getActionName( String newName )
        {
          return creator.getActionName( newName );
        }
      };
      myDialog.show();
      if( myDialog.getExitCode() == OK_EXIT_CODE )
      {
        return created.get();
      }
      return null;
    }
  }

  public interface FileCreator<T>
  {
    T createFile( String name, String enhancedClassName );

    String getActionName( String name );
  }
}
