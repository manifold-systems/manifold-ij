class MyJsClass {
  constructor() {
    this._foo = "later"
  }

  hi2() {
  }

  get Yeah() {
    return this._foo
  }
  set Yeah( value ) {
    this._foo = value
  }
}