package ext.highlight;

public class MyErrors
{
  private void testsManResolveScopeProviderWorks()
  {
    Iterable<String> iter = null;
    iter.filterIndexedToList( (i, e) -> i > 0 && e.contains( "s" ) );
  }
}