package ext.self;

import java.lang.reflect.Array;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import manifold.ext.api.Self;

public class ExerciseSelf<T>
{
  public @Self ExerciseSelf<T> getMe()
  {
    return this;
  }

  public List<@Self ExerciseSelf<T>> getListExerciseSelf()
  {
    return Collections.singletonList( this );
  }

  public List<? extends @Self ExerciseSelf<T>> getListExerciseSelf2()
  {
    return Collections.singletonList( this );
  }

  public List<? extends Map<String, @Self ExerciseSelf<T>>> getListMapExerciseSelf()
  {
    return Collections.singletonList( Collections.singletonMap( "hi", this ) );
  }

  public List<? extends Map<@Self ExerciseSelf<T>, String>> getListMapExerciseSelf2()
  {
    return Collections.singletonList( Collections.singletonMap( this, "hi" ) );
  }

  public List<? extends Map<String, ? extends @Self ExerciseSelf<T>>> getListMapExerciseSelf3()
  {
    return Collections.singletonList( Collections.singletonMap( "hi", this ) );
  }

  public List<? extends Map<? extends @Self ExerciseSelf<T>, String>> getListMapExerciseSelf4()
  {
    return Collections.singletonList( Collections.singletonMap( this, "hi" ) );
  }

  public Map<String, @Self ExerciseSelf<T>> getMapExerciseSelf()
  {
    return Collections.singletonMap( "hi", this );
  }

  public Map<@Self ExerciseSelf<T>, String> getMapExerciseSelf2()
  {
    return Collections.singletonMap( this, "hi" );
  }

  public @Self ExerciseSelf[] getArrayExerciseSelf()
  {
    Object array = Array.newInstance( getClass(), 1 );
    Array.set( array, 0, this );
    return (ExerciseSelf[])array;
  }

  public ExerciseSelf @Self [] getArrayExerciseSelf2()
  {
    return getArrayExerciseSelf();
  }

  static class Bar<T> extends ExerciseSelf<T>
  {
    public void noQualifier()
    {
      ExerciseSelf.Bar<T> bar = getMe();
      List<ExerciseSelf.Bar<T>> list = getListExerciseSelf();
      List<? extends ExerciseSelf.Bar> list2 = getListExerciseSelf2();
      List<? extends Map<String, ExerciseSelf.Bar<T>>> list3 = getListMapExerciseSelf();
      List<? extends Map<ExerciseSelf.Bar<T>, String>> list4 = getListMapExerciseSelf2();
      List<? extends Map<String, ? extends ExerciseSelf.Bar>> list5 = getListMapExerciseSelf3();
      List<? extends Map<? extends ExerciseSelf.Bar, String>> list6 = getListMapExerciseSelf4();
      Map<String, ExerciseSelf.Bar<T>> map = getMapExerciseSelf();
      Map<ExerciseSelf.Bar<T>, String> maps = getMapExerciseSelf2();
      ExerciseSelf.Bar[] bars = getArrayExerciseSelf();
      ExerciseSelf.Bar[] bars2 = getArrayExerciseSelf2();
    }
  }

  public void barQualifier()
  {
    ExerciseSelf.Bar<Date> zeeBar = new ExerciseSelf.Bar<>();
    ExerciseSelf.Bar<Date> bar = zeeBar.getMe();
    List<ExerciseSelf.Bar<Date>> list = zeeBar.getListExerciseSelf();
    List<? extends ExerciseSelf.Bar> list2 = zeeBar.getListExerciseSelf2();
    List<? extends Map<String, ExerciseSelf.Bar<Date>>> list3 = zeeBar.getListMapExerciseSelf();
    List<? extends Map<ExerciseSelf.Bar<Date>, String>> list4 = zeeBar.getListMapExerciseSelf2();
    List<? extends Map<String, ? extends ExerciseSelf.Bar<Date>>> list5 = zeeBar.getListMapExerciseSelf3();
    List<? extends Map<? extends ExerciseSelf.Bar, String>> list6 = zeeBar.getListMapExerciseSelf4();
    Map<String, ExerciseSelf.Bar<Date>> map = zeeBar.getMapExerciseSelf();
    Map<ExerciseSelf.Bar<Date>, String> maps = zeeBar.getMapExerciseSelf2();
    ExerciseSelf.Bar<Date>[] bars = zeeBar.getArrayExerciseSelf();
    ExerciseSelf.Bar<Date>[] bars2 = zeeBar.getArrayExerciseSelf2();
  }

  public void ExerciseSelfQualifier()
  {
    ExerciseSelf<T> ExerciseSelf = this.getMe();
    List<ExerciseSelf<T>> list = this.getListExerciseSelf();
    List<? extends ExerciseSelf<T>> list2 = this.getListExerciseSelf2();
    List<? extends Map<String, ExerciseSelf<T>>> list3 = this.getListMapExerciseSelf();
    List<? extends Map<ExerciseSelf<T>, String>> list4 = this.getListMapExerciseSelf2();
    List<? extends Map<String, ? extends ExerciseSelf<T>>> list5 = this.getListMapExerciseSelf3();
    List<? extends Map<? extends ExerciseSelf<T>, String>> list6 = this.getListMapExerciseSelf4();
    Map<String, ExerciseSelf<T>> map = this.getMapExerciseSelf();
    Map<ExerciseSelf<T>, String> maps = this.getMapExerciseSelf2();
    ExerciseSelf<T>[] ExerciseSelfs = this.getArrayExerciseSelf();
    ExerciseSelf<T>[] ExerciseSelfs2 = this.getArrayExerciseSelf2();
  }

  public void noQualifier()
  {
    List<ExerciseSelf<T>> list = getListExerciseSelf();
    List<? extends ExerciseSelf<T>> list2 = getListExerciseSelf2();
    List<? extends Map<String, ExerciseSelf<T>>> list3 = getListMapExerciseSelf();
    List<? extends Map<ExerciseSelf<T>, String>> list4 = getListMapExerciseSelf2();
    List<? extends Map<String, ? extends ExerciseSelf<T>>> list5 = getListMapExerciseSelf3();
    List<? extends Map<? extends ExerciseSelf<T>, String>> list6 = getListMapExerciseSelf4();
    Map<String, ExerciseSelf<T>> map = getMapExerciseSelf();
    Map<ExerciseSelf<T>, String> maps = getMapExerciseSelf2();
    ExerciseSelf<T>[] ExerciseSelfs = getArrayExerciseSelf();
    ExerciseSelf<T>[] ExerciseSelfs2 = getArrayExerciseSelf2();
  }
}