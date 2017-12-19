class MyJsClass_Caret_GetterDeclaration {
  constructor() {
    this._foo = "later"
  }

  hi() {
  }

  get y<caret>eah() {
    return this._foo
  }
  set yeah( value ) {
    this._foo = value
  }
}