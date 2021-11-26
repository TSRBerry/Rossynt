using System;
using AutoFixture;
using JetBrains.Annotations;
using NUnit.Framework;
using RossyntBackend.Utils;

namespace RossyntBackendUnitTest {
    public class StringExtensionTest {
        [NotNull] private readonly Fixture _fixture = new Fixture();

        // ******************************************************************************** //

        [TestCase("abc", 3, "abc")]
        [TestCase("abc", 2, "ab")]
        [TestCase("abc", 1, "a")]
        [TestCase("abc", 0, "")]
        [TestCase("a😀c", 4, "a😀c")]
        [TestCase("a😀c", 3, "a😀")]
        [TestCase("a😀c", 2, "a")]
        [TestCase("a😀c", 1, "a")]
        [TestCase("a😀c", 0, "")]
        [TestCase("😀", 2, "😀")]
        [TestCase("😀", 1, "")]
        [TestCase("😀", 0, "")]
        public void SurrogateSafeLeft([NotNull] string inputString, int maxLength, [NotNull] string expectedResult) {
            Assert.AreEqual(expectedResult, inputString.SurrogateSafeLeft(maxLength));
        }

        [Test]
        public void SurrogateSafeLeft_ArgumentNullException() {
            const string inputString = null;
            // ReSharper disable once AssignNullToNotNullAttribute
            Assert.Catch<ArgumentNullException>(() => _ = inputString.SurrogateSafeLeft(_fixture.Create<int>()));
        }

        [Test]
        public void SurrogateSafeLeft_ArgumentException_Length_TooSmall() {
            var inputString = _fixture.Create<string>();
            Assert.Catch<ArgumentException>(() => _ = inputString.SurrogateSafeLeft(-1));
        }

        [Test]
        public void SurrogateSafeLeft_ArgumentException_Length_TooLarge() {
            var inputString = _fixture.Create<string>();
            Assert.Catch<ArgumentException>(() => _ = inputString.SurrogateSafeLeft(inputString.Length + 1));
        }
    }
}