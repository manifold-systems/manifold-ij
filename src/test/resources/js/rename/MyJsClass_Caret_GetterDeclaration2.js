class MyJsClass_Caret_GetterDeclaration {
  constructor() {
    this._foo = "later"
  }

  hi() {
  }

  get yeah2() {
    return this._foo
  }
  set yeah( value ) {
    this._foo = value
  }
}